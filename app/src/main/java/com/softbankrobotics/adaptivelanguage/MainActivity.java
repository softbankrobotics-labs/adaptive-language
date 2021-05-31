package com.softbankrobotics.adaptivelanguage;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.EngageHumanBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.aldebaran.qi.sdk.object.conversation.Say;
import com.aldebaran.qi.sdk.object.human.Human;
import com.aldebaran.qi.sdk.object.humanawareness.EngageHuman;
import com.aldebaran.qi.sdk.object.humanawareness.HumanAwareness;
import com.aldebaran.qi.sdk.object.locale.Language;
import com.aldebaran.qi.sdk.object.locale.Region;
import com.google.cloud.android.speech.R;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends RobotActivity implements  RobotLifecycleCallbacks {

    private static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private SpeechService mSpeechService;
    private VoiceRecorder mVoiceRecorder;

    // View references
    public TextView mStatus,mText,mInfo;
    private ImageView resultImg;

    //ChatData references (values to modify)
    final List<String> topicNames = Arrays.asList("welcome","confirmation");//Set your different topics here
    final List<String> locales = Arrays.asList("fr","en","de","es","ja","zh");//Set the code for every language you want here
    public final List<String> keywords= Arrays.asList("Bonjour","Good Morning/Afternoon","Guten Tag","Buenos dias","Konnichiwa","Ni hao");//Set the keyword for every locale (with respect to them)
    private boolean waitConfirmation=false;//Ask an english confirmation before switching language
    private boolean speak=false;

    public Map<String, ChatData> chatDataList = new HashMap<>();

    private Future<Void> currentFuture,engageFuture;
    private Say sayError;
    private QiContext qiContext;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        QiSDK.register(this, this);

        viewInit();
    }

    /**
     * onStart, connect and prepare Speech API
     */
    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, SpeechService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    /**
     * onStop, disconnect from the service, and remove listeners (to avoid using service in background)
     */
    @Override
    protected void onStop() {
        stopVoiceRecorder();
        // Stop Cloud Speech API
        if(mSpeechService!=null){
            mSpeechService.removeListener(mSpeechServiceListener);
            mSpeechService = null;
        }
        if(mServiceConnection!=null) unbindService(mServiceConnection);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        QiSDK.unregister(this, this);
    }

    /**
     * Initialize the layout with flags and keywords provided in global variables (each file has for name the corresponding code)
     */
    private void viewInit(){
        mStatus = findViewById(R.id.status);
        mText = findViewById(R.id.text);
        mInfo=findViewById(R.id.infoLanguage);
        resultImg=findViewById(R.id.result);

        //Set up imageViews and textViews for every locale provided
        for(int i=0;i<locales.size();i=i+1){
            int id=getResources().getIdentifier("kwd"+i, "id", getPackageName());
            TextView country= findViewById(id);
            country.setText(keywords.get(i));
            country.setVisibility(View.VISIBLE);
            id=getResources().getIdentifier("flag"+i, "id", getPackageName());
            ImageView flag = findViewById(id);
            final int resourceId = getResources().getIdentifier(locales.get(i), "drawable", getPackageName());
            flag.setImageResource(resourceId);
        }

        //Confirmation switch
        SwitchCompat confirmation = findViewById(R.id.switchConfirmation);
        confirmation.setOnCheckedChangeListener((buttonView, isChecked) -> waitConfirmation = !waitConfirmation);
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) { super.onSaveInstanceState(outState); }

    /** Check and ask for permissions to Internet and Audio
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length != 1 || grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Start the microphone (shut it down first) to be able to listen the speaker
     */
    private void startVoiceRecorder() {
        if (mVoiceRecorder != null) mVoiceRecorder.stop();
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
        showStatus(true);
    }

    /**
     * If the microphone is listening, shut it down
     */
    private void stopVoiceRecorder() {
        showStatus(false);
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    private void showPermissionMessageDialog() {
        MessageDialogFragment.newInstance(getString(R.string.permission_message))
                .show(getSupportFragmentManager(), FRAGMENT_MESSAGE_DIALOG);
    }

    /**
     * Show the state of the microphone. Status can be listening (speaker can talk in his language) and Say Hello (Pepper is waiting for someone)
     * @param hearingVoice true if Pepper is listening, false otherwise
     */
    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(() -> {
            if(hearingVoice){
                findViewById(R.id.micro).setBackground(ContextCompat.getDrawable(this,R.drawable.circle_blue));
                mText.setText(R.string.speak);
            }else {
                findViewById(R.id.micro).setBackground(ContextCompat.getDrawable(this,R.drawable.circle_grey));
                mText.setText("");
            }
        });
    }

    /**
     * When the focus is gained, build the ChatData using every locale and topics provided. Then start looking for human to engage.
     */
    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        Log.d(TAG, "onRobotFocusedGained");
        this.qiContext=qiContext;

        chatInit();

        HumanAwareness humanAwareness = qiContext.getHumanAwareness();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            runOnUiThread(() -> mStatus.setText(R.string.main));
            humanAwareness.addOnEngagedHumanChangedListener(this::onEngageHuman);
            onEngageHuman(humanAwareness.getEngagedHuman());//When starting the application
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    /**
     * When focus is lost, cancel futures on ChatData and Engage
     */
    @Override
    public void onRobotFocusLost() {
        Log.d(TAG, "onRobotFocusLost");
        if(currentFuture!=null) currentFuture.requestCancellation();
        if(engageFuture!=null) engageFuture.requestCancellation();
    }

    @Override
    public void onRobotFocusRefused(String reason) {
        Log.d(TAG, "onRobotFocusRefused : " + reason);
    }

    /** Initialize ChatData with every topics in every languages, setting bookmarks and values
     */
    private void chatInit(){
        boolean first=true;
        for(String locale : locales){
            try{
                final ChatData chatData = new ChatData(this, qiContext, new Locale(locale), topicNames, true);
                chatData.chat.addOnStartedListener(() -> {
                    if(waitConfirmation){
                        runOnUiThread(() ->mInfo.setText(R.string.wrong));
                        speak=true;
                        chatData.goToBookmarkNewTopic("VALIDATION","confirmation");
                    }
                    else chatData.goToBookmarkNewTopic("START","welcome");
                });

                //Add bookmark listener and end listener only once
                //(will be triggered no matter the language)
                chatData.qiChatbot.addOnEndedListener(endReason ->{
                    if(currentFuture!=null) currentFuture.requestCancellation();
                    Log.i(TAG,"onEnded");
                    speak=false;
                    runOnUiThread(() -> showStatus(true));
                });
                if(first){
                    chatData.qiChatbot.addOnBookmarkReachedListener((bookmark -> {
                        if(bookmark.getName().equals("YES"))
                            runOnUiThread(() -> {
                                mInfo.setText("");
                                speak=false;
                        });
                    }));
                    first=false;
                }

                if(chatData.languageIsInstalled) chatDataList.put(locale, chatData);

            } catch (Exception e){
                Log.i(TAG,locale +" not built");
            }
        }
        sayError = SayBuilder.with(qiContext)
                .withText("Sorry, but this language pack is not installed!!")
                .withLocale(new com.aldebaran.qi.sdk.object.locale.Locale(Language.ENGLISH,Region.UNITED_STATES)).build();
    }

    /**
     * When a human is engaged, starts the microphone to record (if permission). When the human is disengaging, stop it, and cancel the ChatData
     * @param engagedHuman the human engaged
     */
    private void onEngageHuman(Human engagedHuman) {
        if (engagedHuman!=null) {
            EngageHuman engage= EngageHumanBuilder.with(qiContext).withHuman(engagedHuman).build();
            engageFuture=engage.async().run();
            engageFuture.thenConsume(value -> {
                if (value.isDone()) {
                    Log.i(TAG,"Reset Engage");
                    if (currentFuture != null) currentFuture.requestCancellation();
                    runOnUiThread(() -> showStatus(false));
                }
            });
            startVoiceRecorder();
        }
    }

    /**
     * Start the ChatData with the language provided, and goes to topic welcome. If a ChatData is already running cancel it.
     * If the language is not recognized or not installed, it starts the error message.
     * @param code the language's code
     */
    private void startLanguage(String code) {
        try {
            ChatData current = chatDataList.get(code);
            if (current != null && current.languageIsInstalled) {
                if (currentFuture != null) {
                    currentFuture.requestCancellation();
                    currentFuture.thenConsume(value -> currentFuture = current.chat.async().run());
                } else currentFuture = current.chat.async().run();

                //Set the flag of the output language
                runOnUiThread(() -> {
                    final int resourceId = getResources().getIdentifier(code, "drawable", getPackageName());
                    resultImg.setImageResource(resourceId);
                });

                //SET HERE THE CODE TO CREATE THE INTERACTION, CHANGE FRAGMENT,....

            } else {
                Future<Void> sayFuture=sayError.async().run();
                sayFuture.thenConsume(value -> showStatus(true));
                if(!code.equals("und")){
                    runOnUiThread(() -> {
                        int flagExistence = getResources().getIdentifier(code, "drawable", getPackageName());
                        if ( flagExistence != 0 ) {
                            resultImg.setImageResource(flagExistence);
                            mInfo.setText(R.string.unavailable);
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Callbacks used to request the API
     * When the user starts to speak, set the configuration of the service.
     * When the user speaks, it requests the API recognize.
     * When the user stop speaking, ends the service.
     */
    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            if (mSpeechService != null && mVoiceRecorder!=null) mSpeechService.setConfig(mVoiceRecorder.getSampleRate(),"fr-FR");
        }

        @Override
        public void onVoice(byte[] data, int size) {
            if (mSpeechService != null && !speak) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
            if (mSpeechService != null) mSpeechService.finishRecognizing();
        }

    };

    /**
     * Listens for an output to the service. When a sentence is recognized, identify the language of it, and starts the chatData.
     * Done if isFinal is true, meaning if the recognizing process is done.
     */
    private final SpeechService.Listener mSpeechServiceListener = new SpeechService.Listener() {

        @Override
        public void onSpeechRecognized(String text, final boolean isFinal) {
            if(isFinal) mVoiceRecorder.dismiss();
            Log.i(TAG,text);
            if (!TextUtils.isEmpty(text) && isFinal){
                runOnUiThread(() -> {
                    showStatus(false);
                    mInfo.setText(null);
                    LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
                    languageIdentifier.identifyLanguage(text).addOnSuccessListener(languageCode -> {
                        mText.setText(text);
                        Log.i(TAG,"Language Recognized : "+languageCode);
                        String code=languageCode.substring(0,2);
                        startLanguage(code);
                    }).addOnFailureListener(e -> startLanguage("und"));
                });
            }
        }

    };

    /**
     * Create a object of the service, and add the listener to it
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
            mStatus.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) { mSpeechService = null; }
    };

}
