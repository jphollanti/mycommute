package mycommute.mycommut;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    public static final String DIGITRANSIT_GRAPHQL = "https://api.digitransit.fi/routing/v1/routers/hsl/index/graphql";
    RequestQueue myRequestQueue;
    int requestsInQueu = 0;
    List<MyLeg> first = new ArrayList<>();
    List<MyLeg> second = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myRequestQueue = Volley.newRequestQueue(this);


        final Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refresh();
            }
        });

        refresh();
    }

    public void refresh() {
        first.clear();
        second.clear();
        makeRequest(DIGITRANSIT_GRAPHQL, R.raw.plahki);
    }

    private void printResults() {
        for (MyLeg l : first) {
            System.out.println(format(l.getStart()) + " - " + format(l.getEnd()) + " / " +l.getCode());
        }
    }

    private CharSequence format(Date date) {
        return DateFormat.format("hh:mm", date);
    }

    private void makeRequest(final String url, final int planId) {
        requestsInQueu++;
        StringRequest r = new StringRequest(Request.Method.POST, url, new ResponseListener(), new ErrorListener()) {

            @Override
            public String getBodyContentType() {
                return "application/graphql";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    Resources res = getResources();
                    InputStream in_s = res.openRawResource(planId);
                    byte[] b = new byte[in_s.available()];
                    in_s.read(b);
                    return b;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        myRequestQueue.add(r);
    }

    private class ResponseListener implements Response.Listener<String> {
        @Override
        public void onResponse(String response) {
            requestsInQueu--;
            try {
                JSONObject obj = new JSONObject(response);
                JSONArray a = obj.getJSONObject("data").getJSONObject("plan").getJSONArray("itineraries");
                JSONObject leg = null;
                for (int i=0; i<a.length(); i++) {
                    JSONObject lso = a.getJSONObject(i);
                    JSONArray ls = lso.getJSONArray("legs");
                    for (int j=0; j<ls.length(); j++) {
                        JSONObject l = ls.getJSONObject(j);
                        if (l.has("mode") && !Objects.equals(l.getString("mode"), "WALK")) {
                            leg = l;
                            break ;
                        }
                    }
                    if (leg == null) {
                        System.err.println("No suitable leg found");
                        continue;
                    }
                    first.add(new MyLeg(
                            leg.getJSONObject("route").getString("shortName"),
                            new Date(leg.getLong("startTime")),
                            new Date(leg.getLong("endTime"))));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (requestsInQueu == 0) {
                printResults();
            }
        }
    }

    private class ErrorListener implements Response.ErrorListener{
        public void onErrorResponse(VolleyError error) {
            requestsInQueu--;
            error.printStackTrace();
        }
    }

    private class MyLeg {
        final String code;
        final Date start;
        final Date end;

        private MyLeg(String code, Date start, Date end) {
            this.code = code;
            this.start = start;
            this.end = end;
        }

        public Date getEnd() {
            return end;
        }

        public Date getStart() {
            return start;
        }

        public String getCode() {
            return code;
        }
    }
}
