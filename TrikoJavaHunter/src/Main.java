import rx.Observable;
import rx.Subscription;

/**
 * Created by Roman Belkov on 20.10.15.
 */
public class Main {

    static final int MAX_SERVO_ANGLE = 90;
    static final int MAX_ANGLE_X = 60;
    static final int MAX_ANGLE_Y = 60;
    static final int SCALE_CONST_X = 20;
    static final int SCALE_CONST_Y = 20;
    static final int MIN_MASS = 5;

    public static int limitAbs(int border, int value) {
        return Helpers.INSTANCE$.limit(-border, border, value);
    }

    public static int updatePositionX(int x) {
        return limitAbs(MAX_ANGLE_X, x) / SCALE_CONST_X;
    }

    public static int updatePositionY(int y) {
        return limitAbs(MAX_ANGLE_Y, y) / SCALE_CONST_Y;
    }

    public static int limitServo(int value) {
        return limitAbs(MAX_SERVO_ANGLE, value);
    }

    static final ServoType servoType = new ServoType(0, 1400000, 625000, 2175000, 20000000);

    public static void main(String[] args) {

        ObjectSensor objectSensor = new ObjectSensor(VideoSource.VP2);
        Buttons buttons = new Buttons();
        ServoMotor servoX = new ServoMotor(ServoPorts.S3, servoType);
        ServoMotor servoY = new ServoMotor(ServoPorts.S4, servoType);

        buttons.ToObservable().filter(b -> b.getButton() == ButtonEventCode.Left).subscribe(x -> objectSensor.Detect());

        Observable<VideoSensorOutput> sensorOutput = objectSensor.ToObservable();
        Observable<VideoSensorOutput.DetectTarget> targetStream =
                sensorOutput
                        .map(VideoSensorOutput::TryGetTarget)
                        .filter(it -> it != null);
        Subscription targetSetter = targetStream.subscribe(objectSensor::SetDetectTarget);

        Observable<VideoSensorOutput.ObjectLocation> locationStream =
                sensorOutput
                        .map(VideoSensorOutput::TryGetLocation)
                        .filter(it -> it != null && it.getMass() > MIN_MASS);

        Subscription aimX =
                locationStream
                    .scan(0, (acc, loc) -> limitServo(acc - updatePositionX(loc.getX())))
                    .subscribe(servoX);

        Subscription aimY =
                locationStream
                    .scan(0, (acc, loc) -> limitServo(acc - updatePositionX(loc.getY())))
                    .subscribe(servoY);

        objectSensor.Start();
        buttons.Start();
    }
}
