package com.hbouzidi.fiveprayers.shadows;

import android.location.Address;
import android.location.Geocoder;

import com.google.common.base.Preconditions;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Implements(Geocoder.class)
public final class ShadowGeocoder {

    private static boolean isPresent = true;
    private List<Address> fromLocation = new ArrayList<>();

    public ShadowGeocoder() {
        Address newAddress = new Address(Locale.getDefault());
        newAddress.setLatitude(48.9220615);
        newAddress.setLongitude(2.2533313);
        newAddress.setLocality("Colombes");
        newAddress.setCountryName("France");
        newAddress.setCountryCode("FR");

        this.fromLocation.add(newAddress);
    }

    /**
     * @return true by default, or the value specified via {@link #setIsPresent(boolean)}
     */
    @Implementation
    protected static boolean isPresent() {
        return isPresent;
    }

    /**
     * Returns an empty list by default, or the last value set by {@link #setFromLocation(List)}
     *
     * <p>{@param latitude} and {@param longitude} are ignored by this implementation, except to check
     * that they are in appropriate bounds. {@param maxResults} determines the maximum number of
     * addresses to return.
     */
    @Implementation
    protected List<Address> getFromLocation(double latitude, double longitude, int maxResults)
            throws IOException {
        if (isPresent) {
            Preconditions.checkArgument(
                    -90 <= latitude && latitude <= 90, "Latitude must be between -90 and 90, got %s", latitude);
            Preconditions.checkArgument(
                    -180 <= longitude && longitude <= 180,
                    "Longitude must be between -180 and 180, got %s",
                    longitude);
            return fromLocation.subList(0, Math.min(maxResults, fromLocation.size()));
        }
        return Collections.emptyList();
    }

    /**
     * Sets the value to be returned by {@link Geocoder#isPresent()}.
     *
     * <p>This value is reset to true for each test.
     */
    public static void setIsPresent(boolean value) {
        isPresent = value;
    }

    /**
     * Sets the value to be returned by {@link Geocoder#getFromLocation(double, double, int)}.
     */
    public void setFromLocation(List<Address> list) {
        fromLocation = list;
    }

    @Resetter
    public static void reset() {
        isPresent = true;
    }
}
