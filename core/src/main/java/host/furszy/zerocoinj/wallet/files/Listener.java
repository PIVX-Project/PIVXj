package host.furszy.zerocoinj.wallet.files;

import java.io.File;

/**
 * Implementors can do pre/post treatment of the wallet file. Useful for adjusting permissions and other things.
 */
public interface Listener {
    /**
     * Called on the auto-save thread when a new temporary file is created but before the wallet data is saved
     * to it. If you want to do something here like adjust permissions, go ahead and do so.
     */
    void onBeforeAutoSave(File tempFile);

    /**
     * Called on the auto-save thread after the newly created temporary file has been filled with data and renamed.
     */
    void onAfterAutoSave(File newlySavedFile);
}