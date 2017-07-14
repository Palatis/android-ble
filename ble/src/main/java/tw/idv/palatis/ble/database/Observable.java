package tw.idv.palatis.ble.database;

import android.support.annotation.NonNull;

public abstract class Observable<ObserverT> extends android.database.Observable<ObserverT> {
    @Override
    public synchronized void registerObserver(@NonNull ObserverT observer) {
        try {
            super.registerObserver(observer);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public synchronized void unregisterObserver(@NonNull ObserverT observer) {
        try {
            super.unregisterObserver(observer);
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized int numObservers() {
        return mObservers.size();
    }

    protected synchronized void notifyChange(@NonNull Notifier<ObserverT> notifier) {
        for (int i = mObservers.size() - 1; i >= 0; --i)
            notifier.notifyChange(mObservers.get(i));
    }

    public interface Notifier<ObserverT> {
        void notifyChange(@NonNull final ObserverT observer);
    }
}
