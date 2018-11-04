package host.furszy.zerocoinj.protocol;

import java.util.Objects;

public class AccIndexHelper {

    private int height;
    private long checksum;

    public AccIndexHelper(int height, long checksum) {
        this.height = height;
        this.checksum = checksum;
    }

    public int getHeight() {
        return height;
    }

    public long getChecksum() {
        return checksum;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccIndexHelper that = (AccIndexHelper) o;
        return height == that.height &&
                checksum == that.checksum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(height, checksum);
    }
}
