package host.furszy.zerocoinj.wallet;

public class CannotSpendCoinsException extends Exception {
    public CannotSpendCoinsException() {
    }

    public CannotSpendCoinsException(String message) {
        super(message);
    }

    public CannotSpendCoinsException(String message, Throwable cause) {
        super(message, cause);
    }

    public CannotSpendCoinsException(Throwable cause) {
        super(cause);
    }
}
