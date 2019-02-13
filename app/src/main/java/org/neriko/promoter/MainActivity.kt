package org.neriko.promoter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import java.util.*
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.reward.RewardItem
import com.google.android.gms.ads.reward.RewardedVideoAd
import com.google.android.gms.ads.reward.RewardedVideoAdListener
import com.google.android.gms.appinvite.AppInviteInvitation
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.dynamiclinks.DynamicLink
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.functions.FirebaseFunctions
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {

    private val rcSignIn = 123

    val mAuth = FirebaseAuth.getInstance()
    val mData = FirebaseFirestore.getInstance()

    val requestInvite = 322

    private lateinit var mRewardedVideoAd: RewardedVideoAd

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        //Setting up primary winner image
        mData.collection("winners").document("main_winner")
                .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException != null) {
                        Log.w("Promoter", firebaseFirestoreException)
                        return@addSnapshotListener
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val uid = documentSnapshot.getString("uid")!!
                        mData.collection("users").document(uid).get()
                                .addOnSuccessListener { snapshot ->
                                    val shortcode = snapshot.getString("picture_shortcode")!!
                                    val name = snapshot.getString("profile_name")!!
                                    val link = if (shortcode.contains("http")) shortcode
                                    else "https://instagram.com/p/$shortcode/media/?size=l"
                                    Picasso.get().load(link).into(image_main)
                                    image_main.setOnClickListener { view ->
                                        if (mAuth.currentUser != null) {
                                            mData.collection("users").document(mAuth.uid!!).get().addOnSuccessListener { it ->
                                                if (it.getString("profile_name") != null) {
                                                    mData.collection("participators").document(mAuth.uid!!).get().addOnSuccessListener {
                                                        if (it.getLong("value") == null) {
                                                            val map = HashMap<String, Any>()
                                                            map["value"] = 1
                                                            mData.collection("participators").document(mAuth.uid!!).set(map)
                                                            Toast.makeText(this@MainActivity, getString(R.string.now_you_participate), Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(this, getString(R.string.specify_photo), Snackbar.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        val uri = Uri.parse("http://instagram.com/_u/$name")
                                        val likeIng = Intent(Intent.ACTION_VIEW, uri)
                                        likeIng.setPackage("com.instagram.android")
                                        try {
                                            startActivity(likeIng)
                                        } catch (e: ActivityNotFoundException) {
                                            startActivity(Intent(Intent.ACTION_VIEW,
                                                    Uri.parse("http://instagram.com/$name")))
                                        }
                                    }
                                }
                    }
                }

        //Setting up secondary winners recyclerView
        val users = ArrayList<User>()
        val recyclerAdapter = CustomAdapter(users)
        val recyclerView = findViewById<RecyclerView>(R.id.images_container).apply {
            isNestedScrollingEnabled = false
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = recyclerAdapter
        }
        mData.collection("winners").document("winners_array")
                .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException != null) {
                        Log.w("Promoter", firebaseFirestoreException)
                        return@addSnapshotListener
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val jsonArray = JSONArray(documentSnapshot.get("array").toString())
                        for (i in 0 until jsonArray.length()) {
                            mData.collection("users").document(jsonArray.getString(i)).get() //Download winner's user data
                                    .addOnSuccessListener {
                                        if (users.size >= 9) {
                                            users[i] = it.toObject(User::class.java)!!
                                            recyclerAdapter.notifyItemChanged(i)
                                        } else {
                                            users.add(it.toObject(User::class.java)!!)
                                            recyclerAdapter.notifyItemInserted(users.size-1)
                                        }
                                    }
                        }
                    }
                }

        //Check authstate
        mAuth.addAuthStateListener { p0 ->
            if (p0.currentUser != null) {
                fab.visibility = View.INVISIBLE
                profile_management_layout.visibility = View.VISIBLE
                phone_number.text = p0.currentUser!!.phoneNumber

                //Listen for profile name
                mData.collection("users").document(p0.currentUser!!.uid).addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if (documentSnapshot != null) {
                        val name = documentSnapshot.getString("profile_name")
                        if (name == null) {
                            Snackbar.make(main_layout, getString(R.string.specify_photo), Snackbar.LENGTH_LONG)
                            profile_name.text = getString(R.string.specify_photo)
                        } else {
                            profile_name.text = name
                        }
                    } else {
                        Snackbar.make(main_layout, getString(R.string.specify_photo), Snackbar.LENGTH_LONG)
                        profile_name.text = getString(R.string.specify_photo)
                    }
                }

                //Listen for multiplier
                mData.collection("participators").document(p0.currentUser!!.uid).addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if (documentSnapshot != null) {
                        val value = documentSnapshot.getLong("value")
                        if (value == null) {
                            textView11.text = "0x"
                            textView11.setTextColor(resources.getColor(R.color.red))
                        } else {
                            textView11.text = value.toString() + "x"
                            textView11.setTextColor(resources.getColor(R.color.green))
                        }
                    } else {
                        textView11.text = "0x"
                        textView11.setTextColor(resources.getColor(R.color.red))
                    }
                }

                //Listen if any of invites succeded
                mData.collection("invites").whereEqualTo("sender", p0.currentUser!!.uid).addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    if (querySnapshot != null) {
                        val documents = querySnapshot.documents
                        for (document in documents) {
                            if (document.getBoolean("succeeded")!!) {
                                mData.collection("participators").document(p0.currentUser!!.uid).get().addOnSuccessListener {
                                    val value = it.getLong("value")!! + 5
                                    val map = HashMap<String, Any>()
                                    map["value"] = value
                                    mData.collection("participators").document(p0.currentUser!!.uid).set(map)
                                }
                                mData.collection("invites").document(document.id).delete()
                            }
                        }
                    }
                }
            } else {
                fab.visibility = View.VISIBLE
                profile_management_layout.visibility = View.GONE
            }
        }

        //Login on FAB click
        fab.setOnClickListener {
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(Arrays.asList(AuthUI.IdpConfig.PhoneBuilder().build()))
                            .setTheme(R.style.GreenTheme)
                            .build(),
                    rcSignIn)
        }

        sign_out.setOnClickListener { mAuth.signOut() }
        edit_profile.setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        share.setOnClickListener { view ->
            if (textView11.text.toString() == "0x") {
                Snackbar.make(main_layout, getString(R.string.not_participating), Snackbar.LENGTH_LONG).show()
            } else {
                val dialog = AlertDialog.Builder(this)
                        .setView(R.layout.dialog_increase)
                        .setCancelable(true)
                        .setTitle(getString(R.string.increase))
                        .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                dialog.show()

                dialog.findViewById<MaterialButton>(R.id.rewarded_video)!!.setOnClickListener {
                    if (mRewardedVideoAd.isLoaded) {
                        mRewardedVideoAd.show()
                    } else {
                        Snackbar.make(main_layout, getString(R.string.rewarded_ad_not_loaded), Snackbar.LENGTH_LONG).show()
                    }
                }

                dialog.findViewById<MaterialButton>(R.id.read_more)!!.setOnClickListener { _ ->
                    val user = FirebaseAuth.getInstance().currentUser
                    val uid = user!!.uid
                    val link = "https://mygame.promoter.com/?invitedby=$uid"
                    val dynamicLink = FirebaseDynamicLinks.getInstance().createDynamicLink()
                            .setLink(Uri.parse(link))
                            .setDomainUriPrefix("https://promoter.page.link")
                            // Open links with this app on Android
                            .setAndroidParameters(DynamicLink.AndroidParameters.Builder().build())
                            // Open links with com.example.ios on iOS
                            //.setIosParameters(DynamicLink.IosParameters.Builder("https://play.google.com/store/apps/details?id=org.neriko.promoter").build())
                            .buildShortDynamicLink().addOnSuccessListener {
                                val dynamicLinkUri = it.shortLink

                                System.out.println(dynamicLinkUri.toString())
                                val intent = AppInviteInvitation.IntentBuilder(getString(R.string.invite_title))
                                        .setMessage(getString(R.string.invite_message))
                                        .setDeepLink(dynamicLinkUri)
                                        .setCustomImage(Uri.parse("https://psv4.userapi.com/c848424/u64987744/docs/d9/24db7a7d5edf/Screenshot_20181110-194423_Promoter.jpg?extra=B6iQRs-OjSflOVaBugYvJk6nJEfjW445jYB_gjok2vmBHm_NdgLS59XNl2G4UEctol_hTBYzZ0rPO8s5utrrl-qRAXzuc0SMY19Gc4gdOkU_i8eNPo7yjSCYKjm0M3xIrIUIBpD4wLck2uO8eHE0hQ"))
                                        .setCallToActionText(getString(R.string.invitation_cta))
                                        .build()
                                startActivityForResult(intent, requestInvite)
                            }
                }
            }
        }

        kartomin.setOnClickListener {
            if (mAuth.uid == "GU8mK45zniNOgSzGBTRshaRcMny1") {
                startActivity(Intent(this, AdminActivity::class.java))
            } else {
                val uri = Uri.parse("http://instagram.com/_u/" + "kartomin")
                val likeIng = Intent(Intent.ACTION_VIEW, uri)
                likeIng.setPackage("com.instagram.android")
                try {
                    startActivity(likeIng)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://instagram.com/" + "kartomin")))
                }
            }
        }



        //Set up rewarded video ad
        mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this)
        mRewardedVideoAd.rewardedVideoAdListener = object : RewardedVideoAdListener {
            override fun onRewardedVideoAdClosed() {}
            override fun onRewardedVideoAdLeftApplication() {}
            override fun onRewardedVideoAdLoaded() {}
            override fun onRewardedVideoAdOpened() {
                mRewardedVideoAd.loadAd("ca-app-pub-1294193105518981/7051547872",
                        AdRequest.Builder().addTestDevice("678FE3ECA708F35EE865420909F6A2C6").build())
            }
            override fun onRewardedVideoCompleted() {}

            override fun onRewarded(p0: RewardItem?) {

                FirebaseFunctions.getInstance()
                        .getHttpsCallable("reward_video")
                        .call()
                        .addOnSuccessListener {
                            System.out.println("success")
                        }
                        .addOnFailureListener {
                            it.printStackTrace()
                        }
            }

            override fun onRewardedVideoStarted() {}
            override fun onRewardedVideoAdFailedToLoad(p0: Int) {
                mRewardedVideoAd.loadAd("ca-app-pub-1294193105518981/7051547872",
                        AdRequest.Builder().addTestDevice("678FE3ECA708F35EE865420909F6A2C6").build())
            }
        }

        mRewardedVideoAd.loadAd("ca-app-pub-1294193105518981/7051547872",
                AdRequest.Builder().addTestDevice("678FE3ECA708F35EE865420909F6A2C6").build())
//        mRewardedVideoAd.loadAd("ca-app-pub-3940256099942544/5224354917",
//                AdRequest.Builder().build())

        //Firebase invites if invited
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(intent)
                .addOnSuccessListener(this) { pendingDynamicLinkData ->
                    // Get deep link from result (may be null if no link is found)
                    var deepLink: Uri? = null
                    if (pendingDynamicLinkData != null) {
                        deepLink = pendingDynamicLinkData.link
                    }
                    //
                    // If the user isn't signed in and the pending Dynamic Link is
                    // an invitation, sign in the user anonymously, and record the
                    // referrer's UID.
                    //
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null &&
                            deepLink != null &&
                            deepLink.getBooleanQueryParameter("invitedby", false)) {
                        val referrerUid = deepLink.getQueryParameter("invitedby")
                        val info = hashMapOf(
                                "id" to referrerUid
                        )
                        FirebaseFunctions.getInstance()
                                .getHttpsCallable("reward_friend")
                                .call(info)
                                .addOnSuccessListener {
                                    System.out.println("success")
                                }
                                .addOnFailureListener {
                                    it.printStackTrace()
                                }
                    }
                }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // rcSignIn is the request code you passed into startActivityForResult(...) when starting the sign in flow.
        if (requestCode == rcSignIn) {
            val response = IdpResponse.fromResultIntent(data)

            // Successfully signed in
            if (resultCode == Activity.RESULT_OK) {
                Snackbar.make(main_layout, R.string.successfully_signed, Snackbar.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            } else {
                if (response == null) {
                    return
                }

                if (response.error!!.errorCode == ErrorCodes.NO_NETWORK) {
                    Snackbar.make(main_layout, R.string.sign_in_failed_internet, Snackbar.LENGTH_LONG).show()
                    return
                }

                Snackbar.make(main_layout, R.string.sign_in_failed_unknown, Snackbar.LENGTH_LONG).show()
            }
        } else if (requestCode == requestInvite) {
            if (resultCode == RESULT_OK) {
                // Get the invitation IDs of all sent messages
                val ids = AppInviteInvitation.getInvitationIds(resultCode, data!!)
                for (id in ids) {
                    val map = HashMap<String, Any>()
                    map["sender"] = mAuth.uid!!
                    map["succeeded"] = false
                    mData.collection("invites").document(id).set(map)
                }
            }
        }
    }

    private inner class CustomAdapter(val users: ArrayList<User>): RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomAdapter.ViewHolder {
            val itemLayoutView = LayoutInflater.from(parent.context).inflate(R.layout.recycler_item, null)
            return ViewHolder(itemLayoutView)
        }

        override fun getItemCount() = users.size

        override fun onBindViewHolder(holder: CustomAdapter.ViewHolder, position: Int) {
            val link = if (users[position].picture_shortcode.contains("http")) users[position].picture_shortcode
            else "https://instagram.com/p/" + users[position].picture_shortcode + "/media/?size=m"
            Picasso.get().load(link).into(holder.imageView)
            holder.imageView.setOnClickListener { view ->
                if (mAuth.currentUser != null) {
                    mData.collection("users").document(mAuth.uid!!).get().addOnSuccessListener { it ->
                        if (it.getString("profile_name") != null) {
                            mData.collection("participators").document(mAuth.uid!!).get().addOnSuccessListener {
                                if (it.getLong("value") == null) {
                                    val map = HashMap<String, Any>()
                                    map["value"] = 1
                                    mData.collection("participators").document(mAuth.uid!!).set(map)
                                    Toast.makeText(this@MainActivity, getString(R.string.now_you_participate), Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.specify_photo), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                val uri = Uri.parse("http://instagram.com/_u/" + users[position].profile_name)
                val likeIng = Intent(Intent.ACTION_VIEW, uri)
                likeIng.setPackage("com.instagram.android")
                try {
                    startActivity(likeIng)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://instagram.com/" + users[position].profile_name)))
                }
            }
        }

        private inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
            val imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        }
    }
}
