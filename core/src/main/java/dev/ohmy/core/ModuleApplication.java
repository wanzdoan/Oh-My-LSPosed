package dev.ohmy.core;

import android.app.Application;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApplication extends Application
        implements XposedServiceHelper.OnServiceListener {
    public interface ServiceObserver {
        void onXposedServiceChanged(XposedService service);
    }

    private static final CopyOnWriteArrayList<ServiceObserver> OBSERVERS =
            new CopyOnWriteArrayList<>();
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        notifyObservers(boundService);
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
            notifyObservers(null);
        }
    }

    public static XposedService getService() {
        return service;
    }

    public static void addObserver(ServiceObserver observer) {
        OBSERVERS.addIfAbsent(observer);
        observer.onXposedServiceChanged(service);
    }

    public static void removeObserver(ServiceObserver observer) {
        OBSERVERS.remove(observer);
    }

    private static void notifyObservers(XposedService currentService) {
        for (ServiceObserver observer : OBSERVERS) {
            observer.onXposedServiceChanged(currentService);
        }
    }
}
