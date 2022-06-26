package com.cjc.fluid

import android.content.res.Resources
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.plus
import androidx.lifecycle.ViewModel
import com.cjc.fluid.ui.theme.MyProjectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random


class MainViewModel : ViewModel() {
    private val _viewState = MutableStateFlow(listOf<Particle>())
    val viewState: StateFlow<List<Particle>> = _viewState
    private var sph: Sph
    val radius = 6f //粒子半径
    private val column = 40 //x个数
    private val row = 60  // y个数

    //canvas画布大小
    private val displayMetrics = Resources.getSystem().displayMetrics
    val density = displayMetrics.density
    val canvasWidthPx = min(2 * radius.toInt() * column * 3, displayMetrics.widthPixels)
    val canvasHeightPx = (canvasWidthPx * 1).toInt()

    val leftTopOffset = Offset(canvasWidthPx / 2.0f - (column * radius) - 10, 10f)

    init {

        val mutableList = ArrayList<Particle>()

        /**
         * 粒子真实位置=偏移量leftTopOffset + 粒子的自身位置
         */
        for (y in 0 until row) {
            for (x in 0 until column) {
                mutableList.add(Particle(x = PointF(x.toFloat() * radius * 2, y.toFloat() * radius * 2)))
            }
        }
        _viewState.value = mutableList

        sph = Sph(mutableList, radius = radius, wpx = canvasWidthPx, hpx = canvasHeightPx, leftTop = leftTopOffset)

    }

    fun dispatch() {

        sph.run()//计算d_velocity

        _viewState.value = _viewState.value.map {
            /**
             * 第四步，根据d_velocity更新速度，根据速度更新位置
             */
            val v = it.v + (sph.dt * it.d_velocity)

            val x = it.x + (sph.dt * v)

            it.copy(x = x, v = v)
        }
        sph.setList(_viewState.value)
        //处理边界
        enforceBoundary(RectF(0f, 0f, canvasWidthPx.toFloat(), canvasHeightPx.toFloat()))


    }

    /**
     * 边界处理
     */
    private fun enforceBoundary(rectF: RectF) {
        var x: Float
        var y: Float
        sph.getList().forEach {
            x = leftTopOffset.x + it.x.x
            y = leftTopOffset.y + it.x.y
            if (x < rectF.left) {
                sph.simulateCollisions(it, PointF(1f, 0f), abs(rectF.left - x))
            }
            if (x > rectF.right) {
                sph.simulateCollisions(it, PointF(-1f, 0f), abs(rectF.right - x))
            }

            if (y < rectF.top) {
                sph.simulateCollisions(it, PointF(0f, 1f), abs(rectF.top - y))
            }
            if (y > rectF.bottom) {
                sph.simulateCollisions(it, PointF(0f, -1f), abs(rectF.bottom - y))
            }

        }
    }

}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyProjectTheme {
                LaunchedEffect(key1 = Unit) {
                    while (isActive) {
                        delay(100)
                        viewModel.dispatch()
                    }

//                    viewModel.dispatch()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Greeting(viewModel)
                }
            }
        }
    }
}


@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun Greeting(vm: MainViewModel = MainViewModel()) {
    val viewStateFlow = vm.viewState.collectAsState()
    val viewState = viewStateFlow.value
    val sr = vm.radius * 10.0

    Canvas(
        modifier = Modifier
            .width((vm.canvasWidthPx / vm.density).dp)
            .height((vm.canvasHeightPx / vm.density).dp)
            .background(Color.Cyan)
    ) {
        viewState.forEach {
            drawCircle(Color.Blue, vm.radius, vm.leftTopOffset.plus(Offset(it.x.x, it.x.y)))
        }
    }

}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyProjectTheme {
        Column(
            modifier = Modifier
                .fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Greeting()
        }
    }
}