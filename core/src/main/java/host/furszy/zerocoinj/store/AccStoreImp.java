package host.furszy.zerocoinj.store;

import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.accumulators.Accumulator;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.Options;
import org.pivxj.core.Context;
import org.pivxj.core.Utils;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class AccStoreImp implements AccStore {

    private final Context context;
    private DB db;

    private final File path;

    /** Creates a LevelDB SPV block store using the JNI/C++ version of LevelDB. */
    public AccStoreImp(Context context, File directory) throws AccStoreException {
        this(context, directory, JniDBFactory.factory);
    }

    /** Creates a LevelDB SPV block store using the given factory, which is useful if you want a pure Java version. */
    public AccStoreImp(Context context, File directory, DBFactory dbFactory) throws AccStoreException {
        this.context = context;
        this.path = directory;
        Options options = new Options();
        options.createIfMissing();

        try {
            tryOpen(directory, dbFactory, options);
        } catch (IOException e) {
            try {
                dbFactory.repair(directory, options);
                tryOpen(directory, dbFactory, options);
            } catch (IOException e1) {
                throw new AccStoreException(e1);
            }
        }
    }

    private synchronized void tryOpen(File directory, DBFactory dbFactory, Options options) throws IOException {
        db = dbFactory.open(directory, options);
    }


    public synchronized void put(int height, Accumulator acc) throws AccStoreException {
        db.put(
                toKey(height, acc.getDenomination()),
                Utils.serializeBigInteger(acc.getValue())
        );
    }

    public synchronized BigInteger get(int height, CoinDenomination denom) throws AccStoreException {
        byte[] data = db.get(toKey(height,denom));
        if (data == null){
            return null;
        }
        return Utils.unserializeBiginteger(data);
    }

    @Override
    public void truncate() {
        // TODO: delete
    }

    private byte[] toKey(int height, CoinDenomination denom){
        return ByteBuffer.allocate(8).putInt(height).putInt(denom.getDenomination()).array();
    }

}
