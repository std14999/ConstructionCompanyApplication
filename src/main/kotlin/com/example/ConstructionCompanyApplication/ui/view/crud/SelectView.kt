package com.example.ConstructionCompanyApplication.ui.view.crud

import com.example.ConstructionCompanyApplication.ui.configuration.EntityConfigurationProvider
import com.example.ConstructionCompanyApplication.dto.AbstractEntity
import com.example.ConstructionCompanyApplication.ui.controller.CommonController
import com.example.ConstructionCompanyApplication.ui.view.EntityTableView
import com.example.ConstructionCompanyApplication.ui.view.SortBox
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.springframework.data.domain.PageRequest
import tornadofx.*
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class SelectView<T : AbstractEntity>(entityClass: KClass<T>) :
    View(EntityConfigurationProvider.get(entityClass).entityMetadata.name) {
    override val root: BorderPane by fxml("/view/SelectTable.fxml")
    private val selectButton: Button by fxid()
    private val pagination: Pagination by fxid()
    private val pageSizeMenu: MenuButton by fxid()
    private val pageSize25: MenuItem by fxid()
    private val pageSize50: MenuItem by fxid()
    private val pageSize100: MenuItem by fxid()
    private val totalElementsLabel: Label by fxid()
    private val filterGridPane: GridPane by fxid()
    private val sortHBox: HBox by fxid()

    private val tableView = EntityConfigurationProvider.get(entityClass).tableViewFactory.create() as EntityTableView<T>
    private val sortBox = SortBox(tableView)

    private val stage = Stage()
    private val controller = CommonController(entityClass)
    private val itemViewModel = ItemViewModel<T>()

    private val tableViewPagination =  EntityTableViewPagination(
        pagination,
        tableView,
        pageSizeMenu,
        pageSize25,
        pageSize50,
        pageSize100,
        totalElementsLabel
    )

    val selectedEntity = SimpleObjectProperty<T>()

    init {
        initTableView()
        initPagination()
        initSelectButton()
        initStage()
        initSortHBox()
        initSelectButton()
    }

    fun select(): AbstractEntity? {
        tableViewPagination.loadPage(0)
        stage.showAndWait()
        return selectedEntity.value
    }

    private fun initSortHBox() {
        sortHBox.add(sortBox)
        sortBox.directionProperty.addListener { _, _, _ ->
            sortBox.selectColumnProperty.value ?: return@addListener
            tableViewPagination.loadPage(pagination.currentPageIndex)
        }
        sortBox.selectColumnProperty.addListener { _, _, _ ->
            sortBox.directionProperty.value ?: return@addListener
            tableViewPagination.loadPage(pagination.currentPageIndex)
        }
    }

    private fun initStage() {
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.scene = Scene(root)
        stage.title = title
    }

    private fun initTableView() {
        tableView.columnResizePolicy = SmartResize.POLICY
        tableView.items = controller.dataList
        itemViewModel.rebindOnChange(tableView) { selectedEntity ->
            item = selectedEntity
        }
    }

    private fun initPagination() {
        tableViewPagination.pageLoader = {
            pageIndex, pageSize ->
            controller.loadAll(PageRequest.of(pageIndex, pageSize, sortBox.sort))
        }
    }

    private fun initSelectButton() {
        selectButton.disableWhen(itemViewModel.empty)
        selectButton.action {
            selectedEntity.set(itemViewModel.item)
            println("select ${itemViewModel.item}")
            stage.close()
        }
    }
}
