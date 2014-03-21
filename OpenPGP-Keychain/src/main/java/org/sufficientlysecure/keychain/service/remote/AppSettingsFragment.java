/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.service.remote;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import com.beardedhen.androidbootstrap.BootstrapButton;
import org.spongycastle.util.encoders.Hex;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.ui.SelectSecretKeyLayoutFragment;
import org.sufficientlysecure.keychain.ui.adapter.KeyValueSpinnerAdapter;
import org.sufficientlysecure.keychain.util.AlgorithmNames;
import org.sufficientlysecure.keychain.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AppSettingsFragment extends Fragment implements
        SelectSecretKeyLayoutFragment.SelectSecretKeyCallback {

    // model
    private AppSettings mAppSettings;

    // view
    private LinearLayout mAdvancedSettingsContainer;
    private BootstrapButton mAdvancedSettingsButton;
    private TextView mAppNameView;
    private ImageView mAppIconView;
    private Spinner mEncryptionAlgorithm;
    private Spinner mHashAlgorithm;
    private Spinner mCompression;
    private TextView mPackageName;
    private TextView mPackageSignature;

    private SelectSecretKeyLayoutFragment mSelectKeyFragment;

    KeyValueSpinnerAdapter mEncryptionAdapter;
    KeyValueSpinnerAdapter mHashAdapter;
    KeyValueSpinnerAdapter mCompressionAdapter;

    public AppSettings getAppSettings() {
        return mAppSettings;
    }

    public void setAppSettings(AppSettings appSettings) {
        this.mAppSettings = appSettings;
        setPackage(appSettings.getPackageName());
        mPackageName.setText(appSettings.getPackageName());

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(appSettings.getPackageSignature());
            byte[] digest = md.digest();
            String signature = new String(Hex.encode(digest));

            mPackageSignature.setText(signature);
        } catch (NoSuchAlgorithmException e) {
            Log.e(Constants.TAG, "Should not happen!", e);
        }

        mSelectKeyFragment.selectKey(appSettings.getKeyId());
        mEncryptionAlgorithm.setSelection(mEncryptionAdapter.getPosition(appSettings
                .getEncryptionAlgorithm()));
        mHashAlgorithm.setSelection(mHashAdapter.getPosition(appSettings.getHashAlgorithm()));
        mCompression.setSelection(mCompressionAdapter.getPosition(appSettings.getCompression()));
    }

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.api_app_settings_fragment, container, false);
        initView(view);
        return view;
    }

    /**
     * Set error String on key selection
     *
     * @param error
     */
    public void setErrorOnSelectKeyFragment(String error) {
        mSelectKeyFragment.setError(error);
    }

    private void initView(View view) {
        mSelectKeyFragment = (SelectSecretKeyLayoutFragment) getFragmentManager().findFragmentById(
                R.id.api_app_settings_select_key_fragment);
        mSelectKeyFragment.setCallback(this);

        mAdvancedSettingsButton = (BootstrapButton) view
                .findViewById(R.id.api_app_settings_advanced_button);
        mAdvancedSettingsContainer = (LinearLayout) view
                .findViewById(R.id.api_app_settings_advanced);

        mAppNameView = (TextView) view.findViewById(R.id.api_app_settings_app_name);
        mAppIconView = (ImageView) view.findViewById(R.id.api_app_settings_app_icon);
        mEncryptionAlgorithm = (Spinner) view
                .findViewById(R.id.api_app_settings_encryption_algorithm);
        mHashAlgorithm = (Spinner) view.findViewById(R.id.api_app_settings_hash_algorithm);
        mCompression = (Spinner) view.findViewById(R.id.api_app_settings_compression);
        mPackageName = (TextView) view.findViewById(R.id.api_app_settings_package_name);
        mPackageSignature = (TextView) view.findViewById(R.id.api_app_settings_package_signature);

        AlgorithmNames algorithmNames = new AlgorithmNames(getActivity());

        mEncryptionAdapter = new KeyValueSpinnerAdapter(getActivity(),
                algorithmNames.getEncryptionNames());
        mEncryptionAlgorithm.setAdapter(mEncryptionAdapter);
        mEncryptionAlgorithm.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mAppSettings.setEncryptionAlgorithm((int) id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mHashAdapter = new KeyValueSpinnerAdapter(getActivity(), algorithmNames.getHashNames());
        mHashAlgorithm.setAdapter(mHashAdapter);
        mHashAlgorithm.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mAppSettings.setHashAlgorithm((int) id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mCompressionAdapter = new KeyValueSpinnerAdapter(getActivity(),
                algorithmNames.getCompressionNames());
        mCompression.setAdapter(mCompressionAdapter);
        mCompression.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mAppSettings.setCompression((int) id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final Animation visibleAnimation = new AlphaAnimation(0.0f, 1.0f);
        visibleAnimation.setDuration(250);
        final Animation invisibleAnimation = new AlphaAnimation(1.0f, 0.0f);
        invisibleAnimation.setDuration(250);

        // TODO: Better: collapse/expand animation
        // final Animation animation2 = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
        // Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0f,
        // Animation.RELATIVE_TO_SELF, 0.0f);u
        // animation2.setDuration(150);

        mAdvancedSettingsButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mAdvancedSettingsContainer.getVisibility() == View.VISIBLE) {
                    mAdvancedSettingsContainer.startAnimation(invisibleAnimation);
                    mAdvancedSettingsContainer.setVisibility(View.GONE);
                    mAdvancedSettingsButton.setText(getString(R.string.api_settings_show_advanced));
                    mAdvancedSettingsButton.setLeftIcon("fa-caret-up");
                } else {
                    mAdvancedSettingsContainer.startAnimation(visibleAnimation);
                    mAdvancedSettingsContainer.setVisibility(View.VISIBLE);
                    mAdvancedSettingsButton.setText(getString(R.string.api_settings_hide_advanced));
                    mAdvancedSettingsButton.setLeftIcon("fa-caret-down");
                }
            }
        });
    }

    private void setPackage(String packageName) {
        PackageManager pm = getActivity().getApplicationContext().getPackageManager();

        // get application name and icon from package manager
        String appName = null;
        Drawable appIcon = null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);

            appName = (String) pm.getApplicationLabel(ai);
            appIcon = pm.getApplicationIcon(ai);
        } catch (final NameNotFoundException e) {
            // fallback
            appName = packageName;
        }
        mAppNameView.setText(appName);
        mAppIconView.setImageDrawable(appIcon);
    }

    /**
     * callback from select secret key fragment
     */
    @Override
    public void onKeySelected(long secretKeyId) {
        mAppSettings.setKeyId(secretKeyId);
    }

}
