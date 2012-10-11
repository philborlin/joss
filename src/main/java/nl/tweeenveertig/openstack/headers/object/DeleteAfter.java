package nl.tweeenveertig.openstack.headers.object;

import nl.tweeenveertig.openstack.headers.Header;

public class DeleteAfter extends Header {

    public static String X_DELETE_AFTER = "X-Delete-After";

    private long expireAfterSeconds;

    public DeleteAfter(long expireAfterSeconds) {
        this.expireAfterSeconds = expireAfterSeconds;
    }

    @Override
    public String getHeaderValue() {
        return Long.toString(expireAfterSeconds);
    }

    @Override
    public String getHeaderName() {
        return X_DELETE_AFTER;
    }
}