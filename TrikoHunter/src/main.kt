/**
 * Created by Roman Belkov on 16.10.15.
 */

import twitter4j.StatusUpdate
import twitter4j.TwitterFactory
import twitter4j.auth.AccessToken
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder
import java.io.File
import kotlin.concurrent.thread

const val MAX_SERVO_ANGLE = 90
const val MAX_ANGLE_X = 60
const val MAX_ANGLE_Y = 60
const val SCALE_CONST_X = 20
const val SCALE_CONST_Y = 20
const val MIN_MASS = 5

fun limitAbs(border: Int, x: Int) = Helpers.limit(-border, border, x)

fun updatePositionX(x: Int) = limitAbs(MAX_ANGLE_X, x) / SCALE_CONST_X

fun updatePositionY(y: Int) = limitAbs(MAX_ANGLE_Y, y) / SCALE_CONST_Y

val servoSetting = ServoType(stop = 0, zero = 1400000, min = 625000, max = 2175000, period = 20000000)

val twitterThread = thread(start = false) {
    //TODO do not commit it

    val consumerKey = "GULBgxl7WsetWGgExpurrRzc4"
    val consumerSecret = "Vc79kcWEd4pkVUka3e4tolMeJAvw2a7LSAwy6TCV2kG5yzE6Wn"
    val accessToken = "3974223010-vINc48N7DoIZmGk17WUpQxshE0rSGItmymWS3ek"
    val accessTokenSecret = "26ImPowiWzeCB1WmeOhFcPclgonco9y1MO2DD3wQ8Kiv6"

    val token = AccessToken(accessToken, accessTokenSecret)
    val config = ConfigurationBuilder()
            .setOAuthAccessToken(token.token)
            .setOAuthConsumerKey(consumerKey)
            .setOAuthConsumerSecret(consumerSecret)
            .setOAuthAccessTokenSecret(accessTokenSecret).build();
    val auth = OAuthAuthorization(config);

    val twitter = TwitterFactory(config).getInstance(auth)

    val statusUpdate = StatusUpdate("TRIK says hello to Twitter! #TRIK #SECR2015")

    val screenshotName = "/home/root/trik-SECR-image.png"
    Helpers.takeScreenshot(screenshotName)
    statusUpdate.setMedia(File(screenshotName))

    val status = twitter.updateStatus(statusUpdate);
    println("status.toString() = " + status.toString())
}

fun main(args: Array<String>) {
    val objectSensor = ObjectSensor(VideoSource.VP2)
    val buttons = Buttons()
    val servoX = ServoMotor(ServoPorts.S3, servoSetting)
    val servoY = ServoMotor(ServoPorts.S4, servoSetting)

    val sensorOutput = objectSensor.toObservable()
    val targetStream = sensorOutput.map { it.tryGetTarget() }.filter { it != null }
    val targetSetter = targetStream.subscribe { objectSensor.setDetectTarget(it!!) }

    val locationStream = sensorOutput
            .map { it.tryGetLocation() }
            .filter { it != null && it.mass > MIN_MASS }
            .map { it as VideoSensorOutput.ObjectLocation }

    fun limitServo(x: Int) = limitAbs(MAX_SERVO_ANGLE, x)

    val aimX =
            locationStream
                    .scan(0, { acc, loc -> limitServo(acc - updatePositionX(loc.x)) })
                    .subscribe(servoX)

    val aimY =
            locationStream
                    .scan(0, { acc, loc -> limitServo(acc - updatePositionY(loc.y)) })
                    .subscribe(servoY)

    fun Exit() {
        aimX.unsubscribe()
        aimY.unsubscribe()
        targetSetter.unsubscribe()
        servoX.close()
        servoY.close()
        objectSensor.close()
        buttons.close()
        System.exit(0)
    }

    buttons.toObservable().filter { it.button == ButtonEventCode.Left }.subscribe { objectSensor.detect() }
    buttons.toObservable().filter { it.button == ButtonEventCode.Power }.subscribe { Exit() }
    buttons.toObservable().filter { it.button == ButtonEventCode.Up }.subscribe { twitterThread.start() }

    objectSensor.start()
    buttons.start()
}