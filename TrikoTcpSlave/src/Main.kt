import java.io.File

/**
 * Created by Roman Belkov on 21.10.15.
 */

fun main(args: Array<String>) {

    I2cTrik.open()
    File("/sys/class/gpio/gpio62/value").writeText("1")
    I2cTrik.writeWord(MotorPorts.M4.I2cPwmAddress(), 0x1000)
    I2cTrik.writeWord(MotorPorts.M2.I2cPwmAddress(), 0x1000)

    val leftMotor  = PowerMotor(MotorPorts.M2)
    val rightMotor = PowerMotor(MotorPorts.M3)

    val server = GamepadServer(667)

    val gamepadObservable = server.toObservable()

    gamepadObservable
            .map { it as? GamepadEvent.Pad }
            .filter { it != null && it.padId == 2 }
            .map { Pair(it!!.coords.first, it!!.coords.second) }
            .subscribe { val (x, y) = it; leftMotor.setPower(y + x); rightMotor.setPower(y - x) }

    gamepadObservable
            .map { it as? GamepadEvent.PadUp }
            .filter { it != null && it.padId == 2 }
            .subscribe { leftMotor.setPower(0); rightMotor.setPower(0) }

    server.start()

}