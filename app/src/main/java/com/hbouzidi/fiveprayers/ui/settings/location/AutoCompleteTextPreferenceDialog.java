package com.hbouzidi.fiveprayers.ui.settings.location;

import android.content.Context;
import android.content.DialogInterface;
import android.location.Address;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.hbouzidi.fiveprayers.location.SearchHelper;
import com.hbouzidi.fiveprayers.location.osm.NominatimAPIService;
import com.hbouzidi.fiveprayers.location.osm.NominatimAddress;
import com.hbouzidi.fiveprayers.location.osm.NominatimReverseGeocodeResponse;
import com.hbouzidi.fiveprayers.preferences.PreferencesHelper;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AutoCompleteTextPreferenceDialog extends PreferenceDialogFragmentCompat {

    private static final int TRIGGER_AUTO_COMPLETE = 100;
    private static final long AUTO_COMPLETE_DELAY = 500;
    private static final int SEARCH_RESULTS_LIMIT = 5;

    private Handler handler;
    private AutoSuggestAdapter autoSuggestAdapter;
    private Context context;

    private final AutoCompleteTextPreference preference;
    private AutoCompleteLoading mEditText;
    private boolean isSelectedText;
    private Address selectedAddress;

    public AutoCompleteTextPreferenceDialog(AutoCompleteTextPreference preference) {
        this.preference = preference;
        this.isSelectedText = false;

        final Bundle b = new Bundle();
        b.putString(ARG_KEY, preference.getKey());
        setArguments(b);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nexus 7 needs the keyboard hiding explicitly.
        // A flag on the activity in the manifest doesn't
        // apply to the dialog, so needs to be in code:
        Window window = requireActivity().getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    protected View onCreateDialogView(Context context) {
        this.context = context;
        mEditText = new AutoCompleteLoading(this.context);

        autoSuggestAdapter = new AutoSuggestAdapter(this.context, android.R.layout.simple_dropdown_item_1line);

        ProgressBar progressBar = new ProgressBar(this.context, null, android.R.attr.progressBarStyleSmall);
        progressBar.setBackgroundColor(0xFF6DE3D1);
        progressBar.setIndeterminate(true);

        mEditText.setThreshold(3);
        mEditText.setPaddingRelative(20, 30, 30, 20);
        mEditText.setHint(preference.getText());
        mEditText.setLoadingIndicator(progressBar);
        mEditText.setAdapter(autoSuggestAdapter);
        mEditText.setOnItemClickListener(
                (parent, view, position, id) -> {
                    isSelectedText = true;

                    AutoSuggestAdapter adapter = (AutoSuggestAdapter) mEditText.getAdapter();
                    selectedAddress = adapter.getAddress(position);
                });

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int
                    count, int after) {
                isSelectedText = false;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeMessages(TRIGGER_AUTO_COMPLETE);
                handler.sendEmptyMessageDelayed(TRIGGER_AUTO_COMPLETE,
                        AUTO_COMPLETE_DELAY);
            }
        });

        handler = new Handler(msg -> {
            if (msg.what == TRIGGER_AUTO_COMPLETE) {
                if (!TextUtils.isEmpty(mEditText.getText())) {
                    retrieveData(mEditText.getText().toString());
                }
            }
            return false;
        });

        return mEditText;
    }

    @Override
    public void onStart() {
        super.onStart();

        AlertDialog dialog = (AlertDialog) getDialog();
        if (mEditText != null && dialog != null) {
            // In order to prevent dialog from closing we setup its onLickListener this late
            Button neutralButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neutralButton != null) {
                neutralButton.setOnClickListener(
                        v -> {
                        });
            }
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();

        if (positiveResult) {
            String textValue = mEditText.getText().toString();
            if (preference.callChangeListener(textValue) && isSelectedText) {
                preference.setText(textValue);
                NominatimAPIService nominatimAPIService = NominatimAPIService.getInstance();
                compositeDisposable.add(
                        nominatimAPIService.getAddressFromLatLong(selectedAddress.getLatitude(), selectedAddress.getLongitude())
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeWith(new DisposableSingleObserver<NominatimReverseGeocodeResponse>() {
                                    @Override
                                    public void onSuccess(@NonNull NominatimReverseGeocodeResponse nominatimReverseGeocodeResponse) {
                                        NominatimAddress nominatimAddress = nominatimReverseGeocodeResponse.getAddress();
                                        selectedAddress.setCountryCode(nominatimAddress.getCountryCode());
                                        PreferencesHelper.updateAddressPreferences(context, selectedAddress);
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        PreferencesHelper.updateAddressPreferences(context, selectedAddress);
                                    }
                                }));
            }
        }
    }

    private void retrieveData(String str) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(
                SearchHelper.searchForLocation(str, SEARCH_RESULTS_LIMIT, this.context)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSingleObserver<List<Address>>() {
                            @Override
                            public void onSuccess(List<Address> addresses) {
                                ArrayList<String> stringList = new ArrayList<>(SEARCH_RESULTS_LIMIT);

                                for (Address address : new ArrayList<>(addresses)) {

                                    if (address.getLocality() != null && address.getCountryName() != null) {
                                        StringBuilder builder = new StringBuilder();

                                        builder.append(address.getLocality());
                                        builder.append(", ");

                                        if (address.getSubLocality() != null) {
                                            builder.append(address.getSubLocality());
                                            builder.append(", ");
                                        } else if (address.getMaxAddressLineIndex() >= 1 && address.getAddressLine(1) != null) {
                                            builder.append(address.getAddressLine(1));
                                            builder.append(", ");
                                        }

                                        builder.append(address.getCountryName());

                                        stringList.add(builder.toString());
                                        addresses.add(address);
                                    }
                                }

                                if (stringList.size() > 0) {
                                    autoSuggestAdapter.setData(stringList, addresses);
                                    autoSuggestAdapter.notifyDataSetChanged();
                                }
                            }

                            @Override
                            public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                            }
                        }));
    }
}