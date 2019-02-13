package org.neriko.promoter

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        MobileAds.initialize(this, "ca-app-pub-1294193105518981~1237343401")

        val mInterstitialAd = InterstitialAd(this)
        mInterstitialAd.adUnitId = getString(R.string.interstitial_ad_unit_id)
        //mInterstitialAd.adUnitId = "ca-app-pub-3940256099942544/1033173712"
        mInterstitialAd.loadAd(AdRequest.Builder().addTestDevice("678FE3ECA708F35EE865420909F6A2C6").build())
        mInterstitialAd.adListener = object: AdListener() {
            override fun onAdLoaded() {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                mInterstitialAd.show()
            }

            override fun onAdFailedToLoad(errorCode: Int) {
                Toast.makeText(this@SplashActivity, getString(R.string.failed_interstitial), Toast.LENGTH_LONG).show()
                finish()
            }

            override fun onAdClosed() {
                finish()
            }
        }
    }
}
