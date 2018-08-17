package host.furszy.zerocoinj.wallet;

public class InvalidSpendException extends Exception {
    public InvalidSpendException(String s) {
        super(s);
    }

    public InvalidSpendException(Throwable cause) {
        super(cause);
    }
}
