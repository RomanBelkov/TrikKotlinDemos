import rx.Observable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Created by Roman Belkov on 17.10.15.
 */

fun sgn(x: Double) = Math.signum(x)
fun abs(x: Double) = Math.abs(x)

const val c_fb                = 12.7 //full battery value
const val c_p2d               = 0.0175 //parrots to degree
const val c_itnum             = 100 //number of iterations for gyro drift calculations
const val c_minpower          = 5 //min power
const val c_mainperiod : Long = 1 //ms

fun main(args: Array<String>) {

    //init routines, ugly for now
    I2cTrik.open()
    File("/sys/class/gpio/gpio62/value").writeText("1")
    File("/sys/class/misc/l3g42xxd/fs_selection").writeText("1")
    File("/sys/class/misc/l3g42xxd/odr_selection").writeText("2")
    I2cTrik.writeWord(0x12, 0x0500)
    I2cTrik.writeWord(0x13, 0x0500)

    val leftMotor     = PowerMotor(MotorPorts.M3)
    val rightMotor    = PowerMotor(MotorPorts.M4)
    val gyroscope     = Gyroscope()
    val accelerometer = Accelerometer()
    val battery       = Battery()
    val buttons       = Buttons()

    //PIDc
    var c_ck = 0.0044
    var c_pk = 15 //10
    var c_ik = 3  //2
    var c_dk = 12 //10

    //global values
    var g_bc = 1.0 //battery coefficient
    var g_gd = 0
    var g_od = 0.0  //out data (gyro accel fusion)
    var g_angle2 = 0.0
    var g_angle3 = 0.0
    var g_offset = 0.0
    var g_te = System.currentTimeMillis()

    fun Exit() {
        leftMotor.stop()
        rightMotor.stop()
        I2cTrik.close()
        System.exit(0)
    }

    val observableButtons = buttons.toObservable()
    observableButtons.filter { it.button == ButtonEventCode.Power }.subscribe { Exit() }
    observableButtons.filter { it.button == ButtonEventCode.Down }.subscribe {  g_offset = g_od }

    fun setBatteryTimer() {
        fun batteryLoop() {
            g_bc = c_fb / battery.readVoltage()
        }
        Observable.interval(500, TimeUnit.MILLISECONDS).subscribe { batteryLoop() }
    }

    fun calibrateGyrDrift() {
        println("Gyro drift calculating...");
        var gd = 0;
        for(i in 0..c_itnum) {
            gd += gyroscope.read()?.x!!
            Thread.sleep(50)
        }

        g_gd = gd / c_itnum;
        println("Gyro drift is: $g_gd")
    }

    var g_cnt = 0
    fun startBalancing() {
        fun balanceLoop() {
            var accfd = accelerometer.read() //accel full data
            //println("ACC:  ${accfd?.x}, ${accfd?.y}, ${accfd?.z}")
            var accd = -Math.atan2(accfd?.z!!.toDouble(), -accfd?.x!!.toDouble()) * 180.0/3.14159

            var tmp = System.currentTimeMillis() - g_te
            var gyrd = (gyroscope.read()!!.x - g_gd)*tmp.toDouble()*c_p2d/1000.0 //ms to s
            g_te = System.currentTimeMillis()

            g_od = (1 - c_ck)*(g_od + gyrd) + c_ck*accd
            var angle = g_od - g_offset

            var yaw = g_bc*(sgn(angle)*c_minpower + angle*c_pk + (angle - g_angle2)*c_dk + (angle + g_angle2 + g_angle3)*c_ik)
            g_angle3 = g_angle2
            g_angle2 = angle

            //println("Yaw: $yaw  ||| Angle: $angle")
            if (abs(angle) < 45) {
                leftMotor.setPower(yaw.toInt())
                rightMotor.setPower(yaw.toInt())
            } else {
                leftMotor.stop()
                rightMotor.stop()
            }

            if(g_cnt == 20) {
                //println("YAAW: $yaw, ANGLE: $angle, TMP: $tmp")
                g_cnt = 0

            }
            g_cnt += 1
        }

        Observable.interval(c_mainperiod, TimeUnit.MILLISECONDS).subscribe { balanceLoop() }
        print("balancing started")
    }

    buttons.start()
    accelerometer.start()
    gyroscope.start()

    setBatteryTimer()
    calibrateGyrDrift()
    startBalancing()
}