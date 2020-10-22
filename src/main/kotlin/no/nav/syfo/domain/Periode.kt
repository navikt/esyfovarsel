
package no.nav.syfo.domain

import java.time.LocalDate

class Periode() {
    var fom: LocalDate = LocalDate.now()
    var tom: LocalDate = LocalDate.now()
    var grad: Int = 0

    fun withFom(fom: LocalDate): Periode {
        this.fom = fom
        return this
    }

    fun withTom(tom: LocalDate): Periode {
        this.tom = tom
        return this
    }

    fun withGrad(grad: Int): Periode {
        this.grad = grad
        return this
    }
}