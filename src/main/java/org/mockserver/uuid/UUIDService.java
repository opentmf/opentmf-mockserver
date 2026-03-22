package org.mockserver.uuid;

import io.hypersistence.tsid.TSID;

public class UUIDService {

    public static final String FIXED_UUID_FOR_TESTS = UUIDService.getUUID();
    public static boolean fixedUUID = false;

    public static String getUUID() {
        if (!fixedUUID) {
            return TSID.Factory.getTsid().toString();
        } else {
            return FIXED_UUID_FOR_TESTS;
        }
    }

}
