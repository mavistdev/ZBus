package ru.mavist.Zbus_Pskov;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.appmetrica.analytics.AppMetrica;
import io.appmetrica.analytics.AppMetricaConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BusFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final String API_URL = "https://bus.xn--80agkg3am7b1b.xn--p1ai/api/";
    private static final String PREFS_NAME = "BusSchedulePrefs";
    private static final String TAG = "BusFragment";

    private String routeId;
    private TextView scheduleTextView, nextBusTextView, countdownTextView;

    public static BusFragment newInstance(String routeId) {
        BusFragment fragment = new BusFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROUTE_ID, routeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Creating an extended library configuration.
        AppMetricaConfig config = AppMetricaConfig.newConfigBuilder("1a3ef625-568a-4dd3-9ade-ceba5e026e6c").build();
        // Initializing the AppMetrica SDK.
        AppMetrica.activate(getContext(), config);
        if (getArguments() != null) {
            routeId = getArguments().getString(ARG_ROUTE_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bus, container, false);
        scheduleTextView = view.findViewById(R.id.schedule_text);
        nextBusTextView = view.findViewById(R.id.next_bus_text);
        countdownTextView = view.findViewById(R.id.countdown_text);

        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Запускаем обновление или вызываем необходимую функцию
                if (isInternetAvailable()) {
                    fetchBusScheduleOnline();
                    swipeRefreshLayout.setRefreshing(false);  // Останавливаем индикатор загрузки
                } else {
                    loadOfflineSchedule();
                    swipeRefreshLayout.setRefreshing(false);  // Останавливаем индикатор загрузки
                }
                AppMetrica.reportEvent("Refresh complete");
            }
        });


        if (isInternetAvailable()) {
            fetchBusScheduleOnline();
        } else {
            loadOfflineSchedule();
        }

        return view;
    }

    private boolean isInternetAvailable() {
        return NetworkUtils.isInternetAvailable(requireContext());
    }

    private void fetchBusScheduleOnline() {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "API Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String schedule = parseSchedule(response.toString(), routeId);
                    saveScheduleOffline(routeId, response.toString()); // Сохраняем данные локально
                    requireActivity().runOnUiThread(() -> {
                        Log.d("mav", routeId);
                        scheduleTextView.setText(schedule);
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        scheduleTextView.setText("Ошибка загрузки данных: код " + responseCode);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки данных", e);
                requireActivity().runOnUiThread(() -> {
                    scheduleTextView.setText("Ошибка загрузки данных.");
                    loadOfflineSchedule();
                });
            }
        }).start();
    }

    private void loadOfflineSchedule() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonResponse = prefs.getString("schedule_" + routeId, null);

        if (jsonResponse != null) {
            String schedule = parseSchedule(jsonResponse, routeId);
            requireActivity().runOnUiThread(() -> {
                scheduleTextView.setText("Оффлайн режим. \n" +
                        "Используются последние синхронизированные данные " +
                        "\n"+
                        schedule);
            });
        } else {
            requireActivity().runOnUiThread(() -> {
                scheduleTextView.setText("Нет данных для оффлайн-режима.");
            });
        }
    }


    private void saveScheduleOffline(String routeId, String jsonResponse) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("schedule_" + routeId, jsonResponse);
        editor.apply();
    }

    private String parseSchedule(String jsonResponse, String routeId) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject routes = jsonObject.getJSONObject("routes");
            JSONArray routeArray = routes.getJSONArray(routeId);

            List<String[]> scheduleList = new ArrayList<>();
            StringBuilder scheduleText = new StringBuilder();

            if(routeId.equals("308")){
                scheduleText.append("Маршрутка отправляется с остановки \n\"Загорицкая горка\" (ЖК \"Видный\")").append("\n\n");
            }
            for (int i = 0; i < routeArray.length(); i++) {
                JSONObject routeItem = routeArray.getJSONObject(i);
                String time = routeItem.getString("time");
                String comment = routeItem.optString("comment", "");
                scheduleList.add(new String[]{time, comment});
                scheduleText.append(time).append(comment.isEmpty() ? "" : " · " + comment + "").append("\n");
            }

            updateBusInfo(scheduleList); // Обновляем информацию о следующем автобусе
            return scheduleText.toString();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обработки JSON", e);
            return "Ошибка обработки данных.";
        }
    }

    private void updateBusInfo(List<String[]> schedule) {
        Calendar currentTime = Calendar.getInstance();
        int currentHours = currentTime.get(Calendar.HOUR_OF_DAY);
        int currentMinutes = currentTime.get(Calendar.MINUTE);

        String[] nextBus = null;

        for (String[] bus : schedule) {
            int busHours = Integer.parseInt(bus[0].split(":")[0]);
            int busMinutes = Integer.parseInt(bus[0].split(":")[1]);

            // Переходим к первому подходящему времени
            if (busHours > currentHours || (busHours == currentHours && busMinutes > currentMinutes)) {
                nextBus = bus;
                break;
            }
        }

        // Если следующий автобус не найден, берём первый из списка
        if (nextBus == null) {
            nextBus = schedule.get(0);
        }

        String nextBusTime = nextBus[0];
        String nextBusRoute = nextBus[1];
        int nextBusHours = Integer.parseInt(nextBusTime.split(":")[0]);
        int nextBusMinutes = Integer.parseInt(nextBusTime.split(":")[1]);

        // Рассчитываем оставшееся время до следующего автобуса
        int minutesToNextBus = nextBusMinutes - currentMinutes;
        int hoursToNextBus = nextBusHours - currentHours;

        if (minutesToNextBus < 0) {
            minutesToNextBus += 60;
            hoursToNextBus -= 1;
        }
        if (hoursToNextBus < 0) {
            hoursToNextBus += 24; // Учитываем переход на следующий цикл времени
        }

        // Обновляем текстовые поля
        int finalHoursToNextBus = hoursToNextBus;
        int finalMinutesToNextBus = minutesToNextBus;
        requireActivity().runOnUiThread(() -> {
            nextBusTextView.setText("Следующий автобус в " + nextBusTime);
            countdownTextView.setText("Через " + (finalHoursToNextBus > 0 ? finalHoursToNextBus + " ч. " : "") + finalMinutesToNextBus + " мин.");
        });
    }
}
