# Adaptive Language for Pepper

Article and code updated on 26/05/21

This project is a demonstration of how Pepper can adapt its language depending on speaker's language. The speaker just needs to say a keyword in one of the languages on the screen, and Pepper will automatically speak in this language, without any interaction with the tablet.

Tested under NAOqi version 2.9.5 & Google Speech-to-Text 1.23.0 & ML Kit 16.1.1

**Disclaimer : After a few iterations in various languages, recognition by Cloud Speech-To-Text can sometimes stop and display an "Audio Timeout error", while the microphone is still listening. In this case, a simple restart of the application should fix the problem. And if you find why this issue occurs, do not hesitate to contact us!**

## 1.Prerequisites

This sample uses Google Speech API. Therefore, you will need to do a few steps in order to try out this project.
Before starting, you must know this library is not free of charge ([see pricing below](https://gitlab.aldebaran.lan/emea/adaptive-language#13pricing)).

### 1.1.Enable the Speech API

If you have not already done so,
[enable the Google Speech API for your project](https://cloud.google.com/speech/docs/getting-started). You
must be whitelisted to do this.

### 1.2.Set Up to Authenticate With Your Project's Credentials

**This Android app uses a JSON credential file locally stored in the resources.**

In order to try out this sample, visit the [Cloud Console](https://console.cloud.google.com/), and
navigate to:
`API Manager > Credentials > Create credentials > Service account key > New service account`.
Create a new service account, and download the JSON credentials file. Put the file in the app
resources as `app/src/main/res/raw/credential.json`.

### 1.3.Pricing

Googe Cloud Speech-to-Text provides 60 minutes of analysis free of charge every month. Then you will be charged every 15 seconds. A common interaction lasts about 2-4 seconds. Therefore, you will have more than 1000 free interactions every months.

Feature | 0-60 minutes | Over 60 Mins up to 1 Million Mins
------------ | ------------- | -------------
Speech Recognition (without Data Logging - default) | Free |$0.006 / 15 seconds
Speech Recognition (with Data Logging opt-in) | Free | $0.004 / 15 seconds

*Each request is rounded up to the nearest increment of 15 seconds.*

## 2.Set Up Speech Recognition & Language Analysis

This sample uses the microphone of the tablet, and not the one integrated to Pepper. The microphone is turned on every time a human is engaged, and turned off if there is no human (as a test, you can simply hide Peppers' eyes). The human speaks, and the voice is sent to the Speech To Text Service. This service returns an output in a String format, with a degree of confidence on it.

Then, we use ML KIT provided by Google (free library) to perform the language analysis of this text. If the language analysed is part of the languages provided by the application, Pepper will start talking in this language. Then you can set up the workflow of your application using this locale.

### 2.1.Set Your Own Languages

This project can be adapted to any language, as long as this [language is provided by Google Speech-to-Text API](https://cloud.google.com/speech-to-text/docs/languages), and is [available on Pepper](https://command-center.softbankrobotics.com/store/).

It can be adatped up to 6 languages (you will need to adapt the layout and the code to add more).

### 2.2.Set up languages and keywords

To set languages, you need to first make sure this language is installed on Pepper. Then, in MainActivity, update the List called "locales" with languages' codes (only the two first letters with the languages, not the region) :

```final List<String> locales = Arrays.asList("fr","en","de","es","ja","zh");```

Then, update the keywords list, with respect to locales' codes. These keywords will be displayed on the screen.

```final List<String> keywords= Arrays.asList("Bonjour","Good Morning/Afternoon","Guten Tag","buenos dias","konichiwa","ni hao");```

![screen of the application](images/screen.png?raw=true)

### 2.3.Set up flags 

For the layout, you will need to add the flag of every language. To do this, just add the image in png format inside the drawable folder, with a name like : CODE.png (for example, fr.png). 

### 2.4 Confirmation

Once the speaker has selected a language, Pepper can wait for him to confirm his choice. To do so, you just need to set the boolean waitConfirmation to true. Thus, Pepper will ask the speaker to confirm his choice in the language he speaks. He can decide to go back by saying "no" to choose another language.

```private boolean waitConfirmation=true;```

## 3.Limitations

As language recognition is a pretty hard problem to solve, the speech recognition may sometimes misunderstand the speaker, and provide the wrong language when two words have close pronunciations (âllo in German and hello in English for instance). 

We chose to set up the speech recognition using French language, as it is the more flexible among languages. It is possible to be in a french configuration, and yet to say words or sentences from other languages!

However, a better solution would be to send speaker's voice in every language provided by the app, and compare every degree of confidence to select the most accurate output. But, you must not forget that this API is not free (even if you have 60 minutes free of speech analysis) and send the voice multiple times could multiply the number of requests.

## 4.License

This project is licensed under the BSD 3-Clause "New" or "Revised" License - see the [COPYING](COPYING.md) file for details.

The main part of this sample comes from [this Google's Github Repository](https://github.com/GoogleCloudPlatform/android-docs-samples/tree/master/speech/Speech)

See the [LICENSE](LICENSE.txt) file for more information about the License.
