/*
 * SPDX-FileCopyrightText: 2026 The LineageOS project
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.statusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import com.android.internal.R;

import java.util.HashMap;
import java.util.stream.Stream;

public class NetworkTraffic extends TextView {
    private static final String TAG = "NetworkTraffic";

    private static final boolean DEBUG = false;

    // This must match the interface prefix in Connectivity's clatd.c.
    private static final String CLAT_PREFIX = "v4-";

    private static final int MODE_DISABLED = 0;
    private static final int MODE_UPSTREAM_ONLY = 1;
    private static final int MODE_DOWNSTREAM_ONLY = 2;
    private static final int MODE_UPSTREAM_AND_DOWNSTREAM = 3;

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int MESSAGE_TYPE_UPDATE_VIEW = 1;
    private static final int MESSAGE_TYPE_ADD_NETWORK = 2;
    private static final int MESSAGE_TYPE_REMOVE_NETWORK = 3;

    private static final int REFRESH_INTERVAL = 2000;

    private static final int UNITS_KILOBITS = 0;
    private static final int UNITS_MEGABITS = 1;
    private static final int UNITS_KILOBYTES = 2;
    private static final int UNITS_MEGABYTES = 3;
    private static final int UNITS_AUTOBYTES = 4;

    private static final int DISPLAY_STYLE_COMBINED = 0;
    private static final int DISPLAY_STYLE_SEPARATE = 1;

    // Thresholds themselves are always defined in kbps
    private static final long AUTOHIDE_THRESHOLD_KILOBITS = 10;
    private static final long AUTOHIDE_THRESHOLD_MEGABITS = 100;
    private static final long AUTOHIDE_THRESHOLD_KILOBYTES = 8;
    private static final long AUTOHIDE_THRESHOLD_MEGABYTES = 80;

    private final int mTextSize;
    private final Handler mTrafficHandler;
    private final SettingsObserver mObserver;

    private int mMode = MODE_DISABLED;
    private boolean mNetworkTrafficIsVisible;
    private long mTxKbps;
    private long mRxKbps;
    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;
    private boolean mAutoHide;
    private long mAutoHideThreshold;
    private int mUnits;
    private boolean mShowUnits;
    private int mNumberSizePercent;
    private int mUnitSizePercent;
    private int mDisplayStyle;
    private int mIconTint = Color.WHITE;

    // Network tracking related variables
    private ConnectivityManager mConnectivityManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private ConnectivityManager.NetworkCallback mDefaultNetworkCallback;

    private final HashMap<Network, LinkProperties> mLinkPropertiesMap = new HashMap<>();
    // Used to indicate that the set of sources contributing
    // to current stats have changed.
    private boolean mNetworksChanged = true;

    public NetworkTraffic(Context context) {
        this(context, null);
    }

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = getResources();
        mTextSize = resources.getDimensionPixelSize(R.dimen.net_traffic_text_size);
        setTypeface(Typeface.create(resources.getString(
                com.android.internal.R.string.config_headlineFontFamily), Typeface.BOLD));

        setSingleLine(true);
        setVisibility(GONE);
        setTextColor(Color.WHITE);

        mNetworkTrafficIsVisible = false;

        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mTrafficHandler = new Handler(mContext.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_TYPE_PERIODIC_REFRESH:
                        recalculateStats();
                        displayStatsAndReschedule();
                        break;

                    case MESSAGE_TYPE_UPDATE_VIEW:
                        displayStatsAndReschedule();
                        break;

                    case MESSAGE_TYPE_ADD_NETWORK:
                        final LinkPropertiesHolder lph = (LinkPropertiesHolder) msg.obj;
                        mLinkPropertiesMap.put(lph.getNetwork(), lph.getLinkProperties());
                        mNetworksChanged = true;
                        break;

                    case MESSAGE_TYPE_REMOVE_NETWORK:
                        mLinkPropertiesMap.remove((Network) msg.obj);
                        mNetworksChanged = true;
                        break;
                }
            }

            private void recalculateStats() {
                final long now = SystemClock.elapsedRealtime();
                final long timeDelta = now - mLastUpdateTime; /* ms */
                if (timeDelta < REFRESH_INTERVAL * 0.95f) {
                    return;
                }
                // Sum tx and rx bytes from all sources of interest
                long txBytes = 0;
                long rxBytes = 0;
                // Add interface stats, including stats from Clat's IPv4 interface
                // (for applicable IPv6 networks). Stats are 0 if it doesn't exist.
                final String[] ifaces =
                        mLinkPropertiesMap.values()
                                .stream()
                                .map(link -> link.getInterfaceName())
                                .filter(iface -> iface != null)
                                .flatMap(iface -> Stream.of(iface, CLAT_PREFIX + iface))
                                .toArray(String[] ::new);
                for (String iface : ifaces) {
                    final long ifaceTxBytes = TrafficStats.getTxBytes(iface);
                    final long ifaceRxBytes = TrafficStats.getRxBytes(iface);
                    if (DEBUG) {
                        Log.d(TAG,
                                "adding stats from interface " + iface + " txbytes " + ifaceTxBytes
                                        + " rxbytes " + ifaceRxBytes);
                    }
                    txBytes += ifaceTxBytes;
                    rxBytes += ifaceRxBytes;
                }

                final long txBytesDelta = txBytes - mLastTxBytes;
                final long rxBytesDelta = rxBytes - mLastRxBytes;

                if (!mNetworksChanged && timeDelta > 0 && txBytesDelta >= 0 && rxBytesDelta >= 0) {
                    mTxKbps = (long) (txBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                    mRxKbps = (long) (rxBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                } else if (mNetworksChanged) {
                    mTxKbps = 0;
                    mRxKbps = 0;
                    mNetworksChanged = false;
                }
                mLastTxBytes = txBytes;
                mLastRxBytes = rxBytes;
                mLastUpdateTime = now;
            }

            private void displayStatsAndReschedule() {
                final boolean enabled = mMode != MODE_DISABLED && isConnectionAvailable();
                final boolean showUpstream =
                        mMode == MODE_UPSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
                final boolean showDownstream =
                        mMode == MODE_DOWNSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
                final boolean shouldHide = mAutoHide
                        && (!showUpstream || mTxKbps < mAutoHideThreshold)
                        && (!showDownstream || mRxKbps < mAutoHideThreshold);

                if (!enabled || shouldHide) {
                    setText("");
                    setVisibility(GONE);
                } else {
                    final boolean separate =
                            mMode == MODE_UPSTREAM_AND_DOWNSTREAM
                                    && mDisplayStyle == DISPLAY_STYLE_SEPARATE;

                    final CharSequence displayText;
                    if (separate) {
                        setSingleLine(false);
                        setMaxLines(2);
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize * 0.5f);
                        displayText = buildSeparateDisplayText(
                                formatOutput(mTxKbps), formatOutput(mRxKbps));
                    } else {
                        setSingleLine(true);
                        setTextSize(TypedValue.COMPLEX_UNIT_PX,
                                mTextSize * mNumberSizePercent / 100f);
                        if (showUpstream && showDownstream) {
                            displayText = buildDisplayText(formatOutput(mTxKbps + mRxKbps));
                        } else if (showUpstream) {
                            displayText = buildDisplayText(formatOutput(mTxKbps));
                        } else {
                            displayText = buildDisplayText(formatOutput(mRxKbps));
                        }
                    }

                    if (!displayText.toString().contentEquals(getText())) {
                        setText(displayText);
                    }
                    setVisibility(VISIBLE);
                }

                // Schedule periodic refresh
                mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
                if (enabled && mNetworkTrafficIsVisible) {
                    mTrafficHandler.sendEmptyMessageDelayed(
                            MESSAGE_TYPE_PERIODIC_REFRESH, REFRESH_INTERVAL);
                }
            }

            private FormatResult formatOutput(long kbps) {
                final String value;
                int unitid = 0;
                switch (mUnits) {
                    case UNITS_KILOBITS:
                        value = String.format("%d", kbps);
                        unitid = R.string.kilobitspersecond_short;
                        break;
                    case UNITS_MEGABITS:
                        value = formatSpeed((float) kbps / 1000);
                        unitid = R.string.megabitspersecond_short;
                        break;
                    case UNITS_KILOBYTES:
                    case UNITS_AUTOBYTES:
                        if (kbps < 8000 || mUnits == UNITS_KILOBYTES) {
                            value = formatSpeed((float) kbps / 8);
                            unitid = R.string.kilobytespersecond_short;
                            break;
                        }
                    case UNITS_MEGABYTES: {
                        value = formatSpeed((float) kbps / 8000);
                    }
                        unitid = R.string.megabytespersecond_short;
                        break;
                    default:
                        value = "unknown";
                        break;
                }

                String unit = null;
                if (unitid != 0) {
                    unit = mContext.getString(unitid);
                }
                return new FormatResult(value, unit);
            }
        };
        mObserver = new SettingsObserver(mTrafficHandler);
    }

    private CharSequence buildDisplayText(FormatResult result) {
        if (!mShowUnits || result.unit == null) {
            return result.value;
        }
        String full = result.value + " " + result.unit;
        if (mUnitSizePercent != 100) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(full);
            int unitStart = result.value.length() + 1;
            ssb.setSpan(new RelativeSizeSpan(mUnitSizePercent / 100f), unitStart, full.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ssb;
        }
        return full;
    }

    private CharSequence buildSeparateDisplayText(FormatResult upResult, FormatResult downResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(upResult.value);
        if (mShowUnits && upResult.unit != null) {
            sb.append(' ').append(upResult.unit);
        }
        sb.append('\n');
        sb.append(downResult.value);
        if (mShowUnits && downResult.unit != null) {
            sb.append(' ').append(downResult.unit);
        }

        if (mShowUnits && mUnitSizePercent != 100) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(sb.toString());
            String str = sb.toString();
            int nlIdx = str.indexOf('\n');
            int spaceIdx1 = str.indexOf(' ');
            if (spaceIdx1 > 0 && upResult.unit != null && spaceIdx1 < nlIdx) {
                ssb.setSpan(new RelativeSizeSpan(mUnitSizePercent / 100f), spaceIdx1 + 1,
                        spaceIdx1 + 1 + upResult.unit.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int secondLineStart = nlIdx + 1;
            int spaceIdx2 = str.indexOf(' ', secondLineStart);
            if (spaceIdx2 > 0 && downResult.unit != null) {
                ssb.setSpan(new RelativeSizeSpan(mUnitSizePercent / 100f), spaceIdx2 + 1,
                        spaceIdx2 + 1 + downResult.unit.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return ssb;
        }
        return sb.toString();
    }

    /** Sets the text color tint for this view based on the current dark theme state. */
    public void setNetworkTrafficTint(int color) {
        mIconTint = color;
        setTextColor(mIconTint);
    }

    /** Called by the host to notify that the container visibility has changed. */
    public void onVisibilityChanged(boolean isVisible) {
        if (mNetworkTrafficIsVisible != isVisible) {
            mNetworkTrafficIsVisible = isVisible;
            updateViewState();
        }
    }

    private void unregisterNetworkCallbacks() {
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
        if (mDefaultNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
            mDefaultNetworkCallback = null;
        }
    }

    private void manageNetworkCallbacks() {
        if (mMode == MODE_DISABLED) {
            // Unregister callbacks if disabling
            unregisterNetworkCallbacks();
            return;
        }

        // Register callbacks if enabling
        if (mNetworkCallback == null) {
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLinkPropertiesChanged(
                        Network network, LinkProperties linkProperties) {
                    Message msg = new Message();
                    msg.what = MESSAGE_TYPE_ADD_NETWORK;
                    msg.obj = new LinkPropertiesHolder(network, linkProperties);
                    mTrafficHandler.sendMessage(msg);
                }

                @Override
                public void onLost(Network network) {
                    Message msg = new Message();
                    msg.what = MESSAGE_TYPE_REMOVE_NETWORK;
                    msg.obj = network;
                    mTrafficHandler.sendMessage(msg);
                }
            };

            NetworkRequest request =
                    new NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                            .build();

            mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
        }

        if (mDefaultNetworkCallback == null) {
            mDefaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    updateViewState();
                }

                @Override
                public void onLost(Network network) {
                    updateViewState();
                }
            };

            mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mObserver.unobserve();
        unregisterNetworkCallbacks();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_MODE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_UNITS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_NUMBER_SIZE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_UNIT_SIZE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NETWORK_TRAFFIC_DISPLAY_STYLE),
                    false, this, UserHandle.USER_ALL);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private boolean isConnectionAvailable() {
        return mConnectivityManager.getActiveNetwork() != null;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mMode = Settings.Secure.getInt(resolver, Settings.Secure.NETWORK_TRAFFIC_MODE, 0);
        mAutoHide =
                Settings.Secure.getInt(resolver, Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0) == 1;
        mUnits = Settings.Secure.getInt(
                resolver, Settings.Secure.NETWORK_TRAFFIC_UNITS, UNITS_KILOBYTES);
        mShowUnits = Settings.Secure.getInt(
                resolver, Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, 1) == 1;
        mNumberSizePercent = Settings.Secure.getInt(
                resolver, Settings.Secure.NETWORK_TRAFFIC_NUMBER_SIZE, 100);
        mUnitSizePercent = Settings.Secure.getInt(
                resolver, Settings.Secure.NETWORK_TRAFFIC_UNIT_SIZE, 100);
        mDisplayStyle = Settings.Secure.getInt(
                resolver, Settings.Secure.NETWORK_TRAFFIC_DISPLAY_STYLE, 0);

        manageNetworkCallbacks();

        switch (mUnits) {
            case UNITS_KILOBITS:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_KILOBITS;
                break;
            case UNITS_MEGABITS:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_MEGABITS;
                break;
            case UNITS_KILOBYTES:
            case UNITS_AUTOBYTES:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_KILOBYTES;
                break;
            case UNITS_MEGABYTES:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_MEGABYTES;
                break;
            default:
                mAutoHideThreshold = 0;
                break;
        }
        updateViewState();
    }

    private void updateViewState() {
        mTrafficHandler.sendEmptyMessage(MESSAGE_TYPE_UPDATE_VIEW);
    }

    private String formatSpeed(float val) {
        int roundedVal = Math.round(val * 10);
        if (roundedVal % 10 == 0) {
            return String.valueOf(roundedVal / 10);
        } else {
            return String.format("%.1f", (float) roundedVal / 10);
        }
    }

    private static class LinkPropertiesHolder {
        private final Network mNetwork;
        private final LinkProperties mLinkProperties;

        public LinkPropertiesHolder(Network network, LinkProperties linkProperties) {
            mNetwork = network;
            mLinkProperties = linkProperties;
        }

        public Network getNetwork() {
            return mNetwork;
        }

        public LinkProperties getLinkProperties() {
            return mLinkProperties;
        }
    }

    private static class FormatResult {
        final String value;
        final String unit;

        FormatResult(String value, String unit) {
            this.value = value;
            this.unit = unit;
        }
    }
}
