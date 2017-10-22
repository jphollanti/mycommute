package mycommute.mycommut;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Pair;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final Map<String, Pair<String, String>> places;

    static {
        places = new HashMap<>();
        places.put("pla", new Pair<>("60.27562294", "25.03515346"));
        places.put("hki", new Pair<>("60.17127673", "24.94086845"));
        //places.put("ilk", new Pair<>("60.15265122", "24.88013404"));
        places.put("ilk", new Pair<>("60.153329", "24.885273"));
    }

    public static final String DIGITRANSIT_BASE_URL = "https://api.digitransit.fi/routing/v1/routers/hsl/index/graphql";
    public static final int AFTERNOON = 2;
    public static final int MORNING = 1;
    RequestQueue myRequestQueue;
    int requestsInQueue = 0;
    final Object requestsInQueueLock = new Object;
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
        synchronized (requestsInQueueLock) {
            if (requestsInQueue > 0) {
                // already ongoing refresh
                return;
            }
        }
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

        if (hourOfDay > 11) {
            mode = AFTERNOON;
            makeRequest(DIGITRANSIT_BASE_URL, places.get("ilk"), places.get("hki"), first);
            makeRequest(DIGITRANSIT_BASE_URL, places.get("hki"), places.get("pla"), second);
        } else {
            mode = MORNING;
            makeRequest(DIGITRANSIT_BASE_URL, places.get("pla"), places.get("hki"), first);
            makeRequest(DIGITRANSIT_BASE_URL, places.get("hki"), places.get("ilk"), second);
        }

        TextView refreshTxt = (TextView) findViewById(R.id.textView);
        now = new Date();
        refreshTxt.setText(DateFormat.format("HH:mm:ss", now));
    }

    private void printResults() {
        TextView results = (TextView) findViewById(R.id.textView2);
        results.setText("");
        if (mode == MORNING) {
            results.append("PLA - HKI:\n");
        } else {
            results.append("ILK - HKI:\n");
        }

        for (MyLeg l : first) {
            results.append(getResultLine(l));
        }

        TextView results2 = (TextView) findViewById(R.id.textView3);
        results2.setText("");
        if (mode == MORNING) {
            results2.append("HKI - ILK:\n");
        } else {
            results2.append("HKI - PLA:\n");
        }
        for (MyLeg l : second) {
            results2.append(getResultLine(l));
        }
    }

    @NonNull
    private String getResultLine(MyLeg l) {
        return DateFormat.format("HH:mm", l.getStart()) + " - " + DateFormat.format("mm", l.getEnd()) + " / " +l.getCode() + "\n";
    }

    private void makeRequest(final String url, final Pair<String, String> from, final Pair<String, String> to, List<MyLeg> results) {
        StringRequest r = new StringRequest(Request.Method.POST, url, new ResponseListener(results), new ErrorListener()) {

            @Override
            public String getBodyContentType() {
                return "application/graphql";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    Resources res = getResources();
                    InputStream in_s = res.openRawResource(R.raw.fromto);
                    byte[] b = new byte[in_s.available()];
                    in_s.read(b);
                    String s = new String(b);

                    Date _15minago = new Date(new Date().getTime()- 900000);
                    String ss = String.format(s,
                            from.first, from.second,
                            to.first, to.second,
                            DateFormat.format("yyyy-MM-dd", _15minago), DateFormat.format("HH:mm:ss", _15minago));
                    return ss.getBytes();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        myRequestQueue.add(r);
        synchronized (requestsInQueueLock) {
            requestsInQueue++;
        }
    }

    private class ResponseListener implements Response.Listener<String> {

        private final List<MyLeg> results;

        ResponseListener(List<MyLeg> results) {
            super();
            this.results = results;
        }

        @Override
        public void onResponse(String response) {
            try {
                JSONObject obj = new JSONObject(response);
                JSONArray a = obj.getJSONObject("data").getJSONObject("plan").getJSONArray("itineraries");
                for (int i=0; i<a.length(); i++) {
                    JSONObject lso = a.getJSONObject(i);
                    JSONArray ls = lso.getJSONArray("legs");
                    List<JSONObject> legs = new ArrayList<>();
                    for (int j=0; j<ls.length(); j++) {
                        JSONObject l = ls.getJSONObject(j);
                        if (l.has("mode") && !Objects.equals(l.getString("mode"), "WALK")) {
                            legs.add(l);
                        }
                    }
                    Date start = new Date(legs.get(0).getLong("startTime"));
                    Date end = new Date(legs.get(legs.size()-1).getLong("endTime"));
                    List<String> codes = new ArrayList<>();
                    for (JSONObject leg : legs) {
                        codes.add(leg.getJSONObject("route").getString("shortName"));
                    }
                    results.add(new MyLeg(
                            TextUtils.join("-",codes),
                            start,
                            end));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } finally {
                synchronized (requestsInQueueLock) {
                    requestsInQueue--;
                    if (requestsInQueue == 0) {
                        printResults();
                    }
                }
            }
        }
    }

    private class ErrorListener implements Response.ErrorListener{
        public void onErrorResponse(VolleyError error) {
            synchronized (requestsInQueueLock) {
                requestsInQueue--;
            }
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
