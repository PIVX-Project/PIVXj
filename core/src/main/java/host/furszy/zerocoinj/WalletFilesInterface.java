package host.furszy.zerocoinj;

import host.furszy.zerocoinj.wallet.files.Listener;

import java.io.IOException;

public interface WalletFilesInterface {

    /** Shut down auto-saving. */
    void shutdownAndWait();

    /** Queues up a save in the background. Useful for not very important wallet changes. */
    void saveLater();

    /** Actually write the wallet file to disk, using an atomic rename when possible. Runs on the current thread. */
    void saveNow() throws IOException;

    /**
     * The given listener will be called on the autosave thread before and after the wallet is saved to disk.
     */
    void setListener(Listener eventListener);
}
