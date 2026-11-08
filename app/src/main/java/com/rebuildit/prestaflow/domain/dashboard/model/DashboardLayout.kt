package com.rebuildit.prestaflow.domain.dashboard.model

/** Un widget du catalogue et sa visibilité dans la disposition. */
data class DashboardLayoutItem(
    val widget: DashboardWidget,
    val visible: Boolean,
)

/** Disposition ordonnée du dashboard : tous les widgets du catalogue, une seule fois chacun. */
data class DashboardLayout(
    val items: List<DashboardLayoutItem>,
) {
    /** Widgets visibles, dans l'ordre d'affichage. */
    val visibleWidgets: List<DashboardWidget> get() = items.filter { it.visible }.map { it.widget }

    /**
     * Regroupe les widgets visibles en lignes : les widgets HALF consécutifs sont empilés deux par
     * ligne, les widgets FULL occupent une ligne seuls. [hidden] : widgets momentanément
     * exclus du rendu sans toucher à la disposition (ex. fonctionnalité absente de la boutique).
     */
    fun rows(hidden: Set<DashboardWidget> = emptySet()): List<List<DashboardWidget>> {
        val rows = mutableListOf<List<DashboardWidget>>()
        val pendingHalves = mutableListOf<DashboardWidget>()

        fun flushHalves() {
            pendingHalves.chunked(2).forEach { rows += it }
            pendingHalves.clear()
        }
        visibleWidgets.filterNot { it in hidden }.forEach { widget ->
            if (widget.span == DashboardWidgetSpan.HALF) {
                pendingHalves += widget
            } else {
                flushHalves()
                rows += listOf(widget)
            }
        }
        flushHalves()
        return rows
    }

    companion object {
        /** Disposition d'usine : ordre de déclaration du catalogue, visibilité = `defaultEnabled`. */
        val DEFAULT: DashboardLayout =
            DashboardLayout(DashboardWidget.entries.map { DashboardLayoutItem(it, it.defaultEnabled) })

        /**
         * Fusionne une disposition enregistrée (couples id / visibilité, dans l'ordre) avec le
         * catalogue courant : un id inconnu ou en double est ignoré ; un widget du catalogue absent
         * de la sauvegarde est ajouté à la fin selon son `defaultEnabled`.
         */
        fun merge(saved: List<Pair<String, Boolean>>): DashboardLayout {
            val known = LinkedHashMap<DashboardWidget, Boolean>()
            saved.forEach { (id, visible) ->
                val widget = DashboardWidget.fromId(id) ?: return@forEach
                if (widget !in known) known[widget] = visible
            }
            DashboardWidget.entries.forEach { widget ->
                if (widget !in known) known[widget] = widget.defaultEnabled
            }
            return DashboardLayout(known.map { (widget, visible) -> DashboardLayoutItem(widget, visible) })
        }
    }
}
