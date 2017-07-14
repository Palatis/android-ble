package tw.idv.palatis.ble.database;

import android.os.Handler;
import android.support.annotation.NonNull;

public class HandlerObserver<ObserverT> {
    private final Handler mHandler;

    public HandlerObserver(final Handler handler) {
        mHandler = handler;
    }

    @SuppressWarnings("unchecked")
    protected final void dispatchChange(@NonNull Dispatcher<ObserverT> dispatcher) {
        if (mHandler == null)
            dispatcher.dispatchChange((ObserverT) this);
        else
            mHandler.post(() -> dispatcher.dispatchChange((ObserverT) this));
    }

    public interface Dispatcher<ObserverT> {
        void dispatchChange(@NonNull final ObserverT observer);
    }
}
