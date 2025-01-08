package ru.mavist.Zbus_Pskov;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ScheduleLoader {

    private static final String API_URL = "https://bus.xn--80agkg3am7b1b.xn--p1ai/api";

    public static List<BusSchedule> loadSchedule(Context context, String route) {
        List<BusSchedule> schedules = new ArrayList<>();
        if (NetworkUtils.isInternetAvailable(context)) {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray routeArray = jsonResponse.getJSONObject("routes").getJSONArray(route);
                    for (int i = 0; i < routeArray.length(); i++) {
                        JSONObject entry = routeArray.getJSONObject(i);
                        String time = entry.getString("time");
                        String comment = entry.optString("comment", "");
                        schedules.add(new BusSchedule(time, comment));
                    }
                }
            } catch (Exception e) {
                Toast.makeText(context, "Ошибка загрузки расписания", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "Нет интернета. Используется оффлайн расписание.", Toast.LENGTH_SHORT).show();
            schedules = getOfflineSchedule(route);
        }
        return schedules;
    }

    private static List<BusSchedule> getOfflineSchedule(String route) {
        List<BusSchedule> offlineSchedules = new ArrayList<>();
        if ("118".equals(route)) {
            offlineSchedules.add(new BusSchedule("05:35", ""));
            offlineSchedules.add(new BusSchedule("06:24", "ч/з мост А. Невского"));
            // Добавьте остальные записи
        } else if ("308".equals(route)) {
            offlineSchedules.add(new BusSchedule("06:40", "до пл. Ленина"));
            offlineSchedules.add(new BusSchedule("07:00", ""));
            // Добавьте остальные записи
        }
        return offlineSchedules;
    }
}
