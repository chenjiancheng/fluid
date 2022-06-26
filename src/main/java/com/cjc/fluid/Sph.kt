package com.cjc.fluid

import android.graphics.PointF
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import kotlin.math.*
import kotlin.random.Random


data class Particle(
    var x: PointF = PointF(0f, 0f),
    var v: PointF = PointF(5f, 10f),

    var material: Int = 1,
    var density: Double = 0.0,
    var pressure: Double = 0.0,
    var d_velocity: PointF = PointF(0f, 0f),
    var color:Color = Color.Magenta,
) : Cloneable {
    override fun clone(): Particle {
        return try {
            return super.clone() as? Particle ?: Particle()
        } catch (e: CloneNotSupportedException) {
            Particle()
        }
    }
}

class Sph constructor(list: List<Particle>, radius: Float = 0.5f, t: Double = 0.126, wpx: Int, hpx: Int, private val leftTop: Offset) {

    private val dimension = 2 // 2D维度
    private val density0 = 1000.0 //默认密度
    private val viscosity = 0.05

    private var g = 9.8//重力，有的写-9.8，这个跟模型/坐标系有关
    var dt = t
    private var particleRadius = radius
    private var particleDiameter = 2 * particleRadius
    private val supportRadius = particleRadius * 4.0 // 支撑半径
    private var mV = 0.8 * particleDiameter.pow(dimension) // 体积
    private var mass = mV * density0 //质量
    private var stiffness = 50.0 //50.0
    private var exponent = 6.0  //7.0
    private var materialFluid = 1
    private var particleList: List<Particle> = list

    //建立网格
    private val gridColumn = ceil(wpx / supportRadius + 0.5).toInt()
    private val gridRow = ceil(hpx / supportRadius + 0.5).toInt()

    //用于获取附近9个网格位置
    private val nearIndex = listOf<Int>(-gridColumn - 1, -gridColumn, -gridColumn + 1, -1, 0, 1, gridColumn - 1, gridColumn, gridColumn + 1)
    //记录网络里对应有哪些particle
    private val gridMap: SparseArray<ArrayList<Particle>> = SparseArray()

    fun run() {
        updateGridParticle()
        computeDensities()
        computeNonPressureForces()
        computePressureForces()
    }

    fun setList(list: List<Particle>) {
        this.particleList = list
    }

    fun getList(): List<Particle> {
        return particleList
    }

    /**
     * 第一步
     *
     * 计算Density
     */
    private fun computeDensities() {
        particleList.forEach { p_i ->
            p_i.density = 0.0
            //在半径内的点
            val neighbors = getParticleNeighbors(p_i)
            neighbors.forEach { p_j ->
                p_i.density += mV * cubicKernel((p_i.x - p_j.x).norm())
            }
            p_i.density *= density0
        }
    }

    /**
     * 获取粒子支撑半径内的其他粒子
     * o(n^2)
     */
    private fun getParticleNeighbors1(particle: Particle): ArrayList<Particle> {
        val neighbors: ArrayList<Particle> = ArrayList()
        particleList.forEach {
            if (it != particle && (it.x - particle.x).norm() < supportRadius) {
                neighbors.add(it)
            }
        }

        return neighbors
    }

    /**
     * 网格法获取particle
     */
    private fun getParticleNeighbors(particle: Particle): ArrayList<Particle> {
        val neighbors: ArrayList<Particle> = ArrayList()
        val index = getGridIndex(particle)
        nearIndex.forEach {i->
            val gridParticle = gridMap[index+i]
            gridParticle?.forEach {

                if (it != particle && (it.x - particle.x).norm() < supportRadius) {
                    neighbors.add(it)
                }
            }
        }

        return neighbors
    }

    /**
     * 把particle更新到对应的网格里
     */
    @OptIn(ExperimentalGraphicsApi::class)
    private fun updateGridParticle(){
        gridMap.clear()
        particleList.forEach {
            val index = getGridIndex(it)
            val map = gridMap[index]
            if(map == null){
                it.color = Color.hsl(Random.nextFloat()*360,Random.nextFloat(),Random.nextFloat())
                gridMap[index] = arrayListOf(it)
            }else {
                it.color = map[0].color
                map.add(it)
            }
        }
    }
    //获取particle归属网格的下标
    private fun getGridIndex(it:Particle):Int{
        return getCeil((leftTop.y+it.x.y)/supportRadius)*gridColumn + getCeil((leftTop.x+it.x.x)/supportRadius)
    }
    private fun getCeil(v:Double):Int{
        return ceil(v+0.5).toInt()
    }

    /**
     * value of cubic spline smoothing kernel
     */
    private fun cubicKernel(r_norm: Double): Double {
        var res = 0.0
        val h = supportRadius

        var k = 1.0
        when (dimension) {
            1 -> k = 4 / 3.0
            2 -> k = 40 / 7 / Math.PI
            3 -> k = 8 / Math.PI
        }

        k /= h.pow(dimension.toDouble())

        val q = r_norm / h
        if (q <= 1.0) {
            res = if (q <= 0.5) {
                val q2 = q * q
                val q3 = q2 * q
                k * (6.0 * q3 - 6.0 * q2 + 1)
            } else {
                k * 2 * (1 - q).pow(3.0)
            }
        }
        return res
    }


    private fun viscosityForce(p_i: Particle, p_j: Particle, r: PointF): PointF {
        val vXy = (p_i.v - p_j.v).dot(r)
        return 2 * (dimension + 2) * viscosity * (mass / (p_j.density)) * vXy / (r.norm()
            .pow(2) + 0.01 * supportRadius.pow(2)) * cubicKernelDerivative(r)
    }

    private fun cubicKernelDerivative(r: PointF): PointF {
        val h = supportRadius

        var k = 1.0
        when (dimension) {
            1 -> k = 4 / 3.0
            2 -> k = 40 / 7 / Math.PI
            3 -> k = 8 / Math.PI
        }
        k = 6 * k / (h.pow(dimension))
        val rNorm = r.norm()
        val q = rNorm / h

        var res = PointF(0f, 0f)
        if (rNorm > 1e-5 && q <= 1.0) {
            val gradQ = r / (rNorm * h)
            res = if (q <= 0.5) {
                gradQ * (k * q * (3.0 * q - 2.0))
            } else {
                val factor = 1.0 - q
                gradQ * (k * (-factor * factor))
            }
        }
        return res
    }

    private fun pressureForce(p_i: Particle, p_j: Particle, r: PointF): PointF {
        return -density0 * mV * (p_i.pressure / (p_i.density.pow(2)) + p_j.pressure / (p_j.density.pow(2))) * cubicKernelDerivative(r)
    }

    /**
     * 第三步
     */
    private fun computePressureForces() {

        particleList.forEach { p_i ->
            p_i.density = max(p_i.density, density0)
            p_i.pressure = stiffness * ((p_i.density / density0).pow(exponent) - 1.0)
        }
        particleList.forEach { p_i ->
            if (p_i.material == materialFluid) {
                val xi = p_i.x
                var dv = PointF(0f, 0f)
                //在半径内的点
                val neighbors = getParticleNeighbors(p_i)
                neighbors.forEach { p_j ->
                    val xj = p_j.x
                    dv += pressureForce(p_i, p_j, xi - xj)
                }
                p_i.d_velocity += dv
            }
        }
    }

    /**
     * 第二步
     */
    private fun computeNonPressureForces() {
        particleList.forEach { p_i ->
            if (p_i.material == materialFluid) {
                val xi = p_i.x
                var dv = PointF(0f, 0f)
                dv.y = g.toFloat()
                //在半径内的点
                val neighbors = getParticleNeighbors(p_i)
                neighbors.forEach { p_j ->
                    val xj = p_j.x
                    dv += viscosityForce(p_i, p_j, xi - xj)
                }
                p_i.d_velocity = dv
            }

        }
    }


    /**
     * 第五步
     */
    fun simulateCollisions(p_i: Particle, vec: PointF, d: Float) {
        val cF = 0.3
        p_i.x += vec * d.toDouble()

        p_i.v -= (1.0 + cF) * p_i.v.dot(vec) * vec

    }

}




