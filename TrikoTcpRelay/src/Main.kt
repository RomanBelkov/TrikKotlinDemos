import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Created by Roman Belkov on 21.10.15.
 */


fun main(args: Array<String>) {

    I2cTrik.open()
    File("/sys/class/gpio/gpio62/value").writeText("1")
    I2cTrik.writeWord(MotorPorts.M4.I2cPwmAddress(), 0x1000)
    I2cTrik.writeWord(MotorPorts.M3.I2cPwmAddress(), 0x1000)

    val leftMotor  = PowerMotor(MotorPorts.M3)
    val rightMotor = PowerMotor(MotorPorts.M4)
    val server = GamepadServer(666)

    val socket = Socket (args[0], 667);
    val out = PrintWriter (socket.outputStream, true);

    fun sendToClient(request: String) {
        out.println(request)
    }


    val gamepadObservable = server.toObservable()

    gamepadObservable
            .map { it as? GamepadEvent.Pad }
            .filter { it != null && it.padId == 1 }
            .map { Pair(it!!.coords.first, it!!.coords.second) }
            .subscribe { val (x, y) = it; leftMotor.setPower(y + x); rightMotor.setPower(y - x) }

    gamepadObservable
        .map { it as? GamepadEvent.PadUp }
        .filter { it != null && it.padId == 1 }
        .subscribe { leftMotor.setPower(0); rightMotor.setPower(0) }

    gamepadObservable
            .map { it as? GamepadEvent.Pad }
            .filter { it != null && it.padId == 2 }
            .subscribe { sendToClient(it.toString()) }

    gamepadObservable
            .map { it as? GamepadEvent.PadUp }
            .filter { it != null && it.padId == 2 }
            .subscribe { sendToClient(it.toString()) }

    server.start()
}