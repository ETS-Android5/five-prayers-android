package com.hbouzidi.fiveprayers.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.hbouzidi.fiveprayers.location.photon.Feature;
import com.hbouzidi.fiveprayers.location.photon.PhotonAPIResponse;
import com.hbouzidi.fiveprayers.location.photon.PhotonAPIService;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;

/**
 * @author Hicham Bouzidi Idrissi
 * Github : https://github.com/Five-Prayers/five-prayers-android
 * licenced under GPLv3 : https://www.gnu.org/licenses/gpl-3.0.en.html
 */
@Singleton
public class AddressSearchService {

    private final PhotonAPIService photonAPIService;

    @Inject
    public AddressSearchService(PhotonAPIService photonAPIService) {
        this.photonAPIService = photonAPIService;
    }

    public Single<List<Address>> searchForLocation(final String locationName, final int limit, final Context context) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());

        return Single.create(emitter -> {
            Thread thread = new Thread(() -> {

                try {
                    List<Address> addressList = geocoder.getFromLocationName(locationName, limit);
                    if (addressList != null && addressList.size() > 0) {
                        emitter.onSuccess(addressList);
                    } else {
                        ArrayList<Address> addresses = new ArrayList<>();

                        PhotonAPIResponse photonAPIResponse = photonAPIService.search(locationName, limit);

                        if (photonAPIResponse != null) {
                            ArrayList<Feature> features = photonAPIResponse.getFeatures();

                            for (Feature feature : features) {
                                String locality = feature.getProperties().getName();
                                String country = feature.getProperties().getCountry();
                                boolean isPlaceType =
                                        feature.getProperties().getOsmKey().equals("place") ||
                                                feature.getProperties().getOsmKey().equals("boundary");

                                if (isPlaceType && StringUtils.isNotBlank(locality) && StringUtils.isNotBlank(country)) {
                                    Address address = new Address(Locale.getDefault());
                                    address.setLocality(locality);
                                    if (feature.getProperties().getState() != null) {
                                        address.setSubLocality(feature.getProperties().getState());
                                        address.setAddressLine(1, feature.getProperties().getState());
                                    }
                                    address.setCountryName(country);
                                    address.setCountryCode(feature.getProperties().getCountryCode());
                                    address.setLongitude(feature.getGeometry().getCoordinates().get(0));
                                    address.setLatitude(feature.getGeometry().getCoordinates().get(1));

                                    addresses.add(address);
                                }
                            }
                            emitter.onSuccess(addresses);
                        }
                    }
                } catch (IOException e) {
                    emitter.onError(e);
                }
            });
            thread.start();
        });
    }
}
