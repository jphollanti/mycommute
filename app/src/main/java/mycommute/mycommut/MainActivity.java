package mycommute.mycommut;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    public static final String DIGITRANSIT_BASE_URL = "https://api.digitransit.fi/routing/v1/routers/hsl/index/graphql";
    RequestQueue myRequestQueue;
    int requestsInQueue = 0;
    List<MyLeg> first = new ArrayList<>();
    List<MyLeg> second = new ArrayList<>();
    int mode = 1;

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

        TextView res1 = (TextView) findViewById(R.id.textView2);
        res1.setText(" ... ");
        TextView res2 = (TextView) findViewById(R.id.textView3);
        res2.setText(" ... ");

        Date now = new Date();
        Calendar c = GregorianCalendar.getInstance();
        c.setTime(now);
        int hourOfDay = c.get(Calendar.HOUR_OF_DAY);

        if (hourOfDay > 12) {
            mode = 1;
            makeRequest(DIGITRANSIT_BASE_URL, R.raw.ilkhki, first);
            makeRequest(DIGITRANSIT_BASE_URL, R.raw.hkipla, second);
        } else {
            mode = 2;
            makeRequest(DIGITRANSIT_BASE_URL, R.raw.plahki, first);
            makeRequest(DIGITRANSIT_BASE_URL, R.raw.hkiilk, second);
        }

        TextView refreshTxt = (TextView) findViewById(R.id.textView);
        now = new Date();
        refreshTxt.setText(DateFormat.format("HH:mm:ss", now));
    }

    private void printResults() {
        TextView results = (TextView) findViewById(R.id.textView2);
        results.setText("");
        if (mode == 1) {
            results.append("PLA - HKI:\n");
        } else {
            results.append("ILK - HKI:\n");
        }

        for (MyLeg l : first) {
            results.append(format(l.getStart()) + " - " + format(l.getEnd()) + " / " +l.getCode() + "\n");
        }

        TextView results2 = (TextView) findViewById(R.id.textView3);
        results2.setText("");
        if (mode == 1) {
            results2.append("HKI - ILK:\n");
        } else {
            results2.append("HKI - PLA:\n");
        }
        for (MyLeg l : second) {
            results2.append(format(l.getStart()) + " - " + format(l.getEnd()) + " / " +l.getCode() + "\n");
        }
    }

    private CharSequence format(Date date) {
        return DateFormat.format("hh:mm", date);
    }

    private void makeRequest(final String url, final int planId, List<MyLeg> results) {
        requestsInQueue++;
        StringRequest r = new StringRequest(Request.Method.POST, url, new ResponseListener(results), new ErrorListener()) {

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
                    String s = new String(b);

                    Date _15minago = new Date(new Date().getTime()- 900000);
                    String ss = String.format(s, DateFormat.format("yyyy-MM-dd", _15minago), DateFormat.format("HH:mm:ss", _15minago));
                    return ss.getBytes();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        myRequestQueue.add(r);
    }

    private class ResponseListener implements Response.Listener<String> {

        private final List<MyLeg> results;

        ResponseListener(List<MyLeg> results) {
            super();
            this.results = results;
        }

        @Override
        public void onResponse(String response) {
            requestsInQueue--;
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
                    results.add(new MyLeg(
                            leg.getJSONObject("route").getString("shortName"),
                            new Date(leg.getLong("startTime")),
                            new Date(leg.getLong("endTime"))));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (requestsInQueue == 0) {
                printResults();
            }
        }
    }

    private class ErrorListener implements Response.ErrorListener{
        public void onErrorResponse(VolleyError error) {
            requestsInQueue--;
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

        Date getEnd() {
            return end;
        }

        Date getStart() {
            return start;
        }

        String getCode() {
            return code;
        }
    }
}
