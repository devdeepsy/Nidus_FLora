package com.devdeepsy.nidusflora

import android.app.Activity
import android.view.ViewGroup
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsLoadOptions
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.BannerView.IListener
import com.unity3d.services.banners.UnityBannerSize // Correct import for banner sizes
import java.util.concurrent.TimeUnit

object AdManager {

    // ---------- YOUR UNITY ADS IDs ----------
    private const val GAME_ID = "800078992"
    private const val INTERSTITIAL_PLACEMENT = "NidusInterstitial"
    private const val REWARDED_PLACEMENT = "NidusRewarded"
    private const val BANNER_PLACEMENT = "NidusBanner"

    // ---------- COOLDOWN ----------
    private const val COOLDOWN_SECONDS = 20L
    private var lastInterstitialShowTime = 0L

    // ---------- STATE ----------
    private var isInitialized = false
    private var isBannerShowing = false
    private var bannerView: BannerView? = null

    // Tracking load states manually since UnityAds.isReady() no longer exists
    private var isInterstitialLoaded = false
    private var isRewardedLoaded = false

    // ---------- CALLBACK ----------
    interface RewardedAdCallback {
        fun onRewardEarned()
        fun onAdFailedToShow()
    }

    // ---------- INITIALIZATION ----------
    fun initialize(activity: Activity, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            onComplete?.invoke()
            return
        }

        UnityAds.initialize(
            activity,
            GAME_ID,
            false,  // test mode
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    isInitialized = true
                    println("✅ Unity Ads initialized")
                    preloadInterstitial()
                    preloadRewarded()
                    onComplete?.invoke()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    println("❌ Init failed: $message")
                    onComplete?.invoke()
                }
            }
        )
    }

    // ---------- INTERSTITIAL ----------
    private fun preloadInterstitial() {
        if (!isInitialized) return
        isInterstitialLoaded = false // Reset state before loading
        UnityAds.load(
            INTERSTITIAL_PLACEMENT,
            UnityAdsLoadOptions(),
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    println("✅ Interstitial loaded")
                    isInterstitialLoaded = true
                }
                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    println("❌ Interstitial load failed: $message")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        preloadInterstitial()
                    }, 5000)
                }
            }
        )
    }

    fun showInterstitialIfAvailable(activity: Activity): Boolean {
        val now = System.currentTimeMillis()
        if (TimeUnit.MILLISECONDS.toSeconds(now - lastInterstitialShowTime) < COOLDOWN_SECONDS) {
            println("⏳ Cooldown active")
            return false
        }
        if (!isInterstitialLoaded) {
            println("⚠️ Interstitial not ready")
            preloadInterstitial()
            return false
        }

        // Reset state immediately upon calling show to prevent double-clicks
        isInterstitialLoaded = false

        UnityAds.show(
            activity,
            INTERSTITIAL_PLACEMENT,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    println("❌ Show failed: $message")
                    preloadInterstitial()
                }
                override fun onUnityAdsShowStart(placementId: String?) {}
                override fun onUnityAdsShowClick(placementId: String?) {}
                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    completionState: UnityAds.UnityAdsShowCompletionState?
                ) {
                    lastInterstitialShowTime = System.currentTimeMillis()
                    preloadInterstitial()
                }
            }
        )
        return true
    }

    // ---------- REWARDED ----------
    private fun preloadRewarded() {
        if (!isInitialized) return
        isRewardedLoaded = false // Reset state before loading
        UnityAds.load(
            REWARDED_PLACEMENT,
            UnityAdsLoadOptions(),
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    println("✅ Rewarded loaded")
                    isRewardedLoaded = true
                }
                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    println("❌ Rewarded load failed: $message")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        preloadRewarded()
                    }, 5000)
                }
            }
        )
    }

    fun showRewardedVideo(activity: Activity, callback: RewardedAdCallback?): Boolean {
        if (!isRewardedLoaded) {
            println("⚠️ Rewarded not ready")
            preloadRewarded()
            callback?.onAdFailedToShow()
            return false
        }

        // Reset state immediately upon calling show
        isRewardedLoaded = false

        UnityAds.show(
            activity,
            REWARDED_PLACEMENT,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    callback?.onAdFailedToShow()
                    preloadRewarded()
                }
                override fun onUnityAdsShowStart(placementId: String?) {}
                override fun onUnityAdsShowClick(placementId: String?) {}
                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    completionState: UnityAds.UnityAdsShowCompletionState?
                ) {
                    if (completionState == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        callback?.onRewardEarned()
                    } else {
                        callback?.onAdFailedToShow()
                    }
                    preloadRewarded()
                }
            }
        )
        return true
    }

    // ---------- BANNER ----------
    fun showBanner(activity: Activity, container: ViewGroup) {
        if (isBannerShowing) hideBanner()

        bannerView = BannerView(activity, BANNER_PLACEMENT, UnityBannerSize(320, 50)).apply {
            setListener(object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    isBannerShowing = true
                    println("✅ Banner loaded: ${bannerAdView?.placementId}")
                }

                override fun onBannerShown(bannerAdView: BannerView?) {
                    println("📺 Banner shown")
                }

                override fun onBannerClick(bannerAdView: BannerView?) {
                    println("🖱️ Banner clicked")
                }

                override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
                    println("❌ Banner error: ${errorInfo?.errorMessage}")
                    isBannerShowing = false

                    // Retry logic
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isBannerShowing) showBanner(activity, container)
                    }, 5000)
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView?) {
                    println("🏳️ User left application via banner")
                }
            })
            load()
        }

        container.addView(
            bannerView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    fun hideBanner() {
        bannerView?.destroy()
        bannerView = null
        isBannerShowing = false
    }

    fun isInterstitialReady(): Boolean = isInterstitialLoaded
    fun isRewardedReady(): Boolean = isRewardedLoaded
}