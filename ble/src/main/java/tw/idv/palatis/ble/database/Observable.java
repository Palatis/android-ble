package tw.idv.palatis.ble.database;

import android.support.annotation.NonNull;

import java.util.ArrayList;

public abstract class Observable<ObserverT> extends android.database.Observable<ObserverT> {
    @Override
    public void registerObserver(@NonNull ObserverT observer) {
        try {
            synchronized (mObservers) {
                super.registerObserver(observer);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void unregisterObserver(@NonNull ObserverT observer) {
        try {
            synchronized (mObservers) {
                super.unregisterObserver(observer);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    public int numObservers() {
        synchronized (mObservers) {
            return mObservers.size();
        }
    }

    protected void notifyChange(@NonNull Notifier<ObserverT> notifier) {
        final ArrayList<ObserverT> observers;
        synchronized (mObservers) {
            observers = new ArrayList<>(mObservers);
        }
        for (int i = observers.size() - 1; i >= 0; --i)
            notifier.notifyChange(observers.get(i));
    }

    public interface Notifier<ObserverT> {
        void notifyChange(@NonNull final ObserverT observer);
    }
}
