package host.furszy.zerocoinj.wallet;

public class CannotCompleteSendRequestException extends Exception {

    public CannotCompleteSendRequestException(String message) {
        super(message);
    }

    public CannotCompleteSendRequestException(String message, Exception cause) {
        super(message, cause);
    }
}
