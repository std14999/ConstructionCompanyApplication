package com.example.ConstructionCompanyApplication.ui.view

import com.example.ConstructionCompanyApplication.dto.*
import com.example.ConstructionCompanyApplication.dto.query.MaterialConsumptionReport
import com.example.ConstructionCompanyApplication.dto.query.Report
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.util.Callback
import tornadofx.*
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1


@Suppress("UNCHECKED_CAST")
class EntityTableView<T : AbstractEntity> : TableView<T>() {
    val validator = EntityValidator<T>()

    private val editablePropertyHandlerList =
        mutableListOf<Triple<KFunction<Unit>, String, KProperty1<T, ObjectProperty<*>>>>()
    private val propertyColumnMap = mutableMapOf<KProperty1<T, ObjectProperty<*>>, TableColumn<T, *>>()
    private val _readonlyProperties = mutableSetOf<KProperty1<T, ObjectProperty<*>>>()
    val readonlyProperties: Collection<KProperty1<T, ObjectProperty<*>>> = _readonlyProperties

    val propertyColumns: Collection<TableColumn<T, *>>
        get() = propertyColumnMap.values

    init {
        addNumerationColumn()
        addValidation()
    }


    private fun addValidation() {
        var prevValue: Any? = null
        this.onEditStart {
            prevValue = this.oldValue
        }
        this.onEditCommit {
            validator.item = rowValue
            val property = getPropertyBy(tableColumn)
            if (!validator.isValid(property, newValue)) {
                getPropertyBy(tableColumn).get(rowValue).value = prevValue
                val dirtyValue = editModel.items[rowValue]?.dirtyColumns?.get(tableColumn as TableColumn<T, Any?>)
                if (prevValue == dirtyValue) {
                    editModel.items[rowValue]?.dirtyColumns?.remove(tableColumn as TableColumn<T, Any?>)
                }
                alert(Alert.AlertType.ERROR, "Введено некорректное значение", validator.getErrorMessage(property))
            }
        }
    }

    private fun addNumerationColumn() {
        val numerationColumn: TableColumn<T, String> = TableColumn("#")
        numerationColumn.setCellValueFactory {
            ReadOnlyObjectWrapper(items.indexOf(it.value).toString())
        }
        numerationColumn.cellFormat {
            style {
                backgroundColor += Color.LIGHTGRAY
                fontWeight = FontWeight.BOLD
            }
            text = items.indexOf(tableRow?.item).toString()
            alignment = Pos.CENTER
        }
        numerationColumn.isSortable = false
        addColumnInternal(numerationColumn)
    }

    fun setReadonly(property: KProperty1<T, ObjectProperty<*>>) {
        _readonlyProperties.add(property)
    }

    fun isReadonly(property: KProperty1<T, ObjectProperty<*>>) = property in _readonlyProperties


    fun addColumns(vararg pairs: Pair<String, KProperty1<T, SimpleObjectProperty<*>>>) =
        pairs.forEach { addColumn(it.first, it.second) }

    fun addColumn(name: String, property: KProperty1<T, SimpleObjectProperty<*>>) {
        when (val genericArgumentClass = property.returnType.arguments[0].type!!.classifier as KClass<*>) {
            LocalDate::class -> {
                property as KProperty1<T, SimpleObjectProperty<LocalDate>>
                addDateColumn(name, property)
            }
            Long::class -> {
                property as KProperty1<T, SimpleObjectProperty<Long>>
                addLongColumn(name, property)
            }
            Int::class -> {
                property as KProperty1<T, SimpleObjectProperty<Int>>
                addIntegerColumn(name, property)
            }
            Boolean::class -> {
                property as KProperty1<T, SimpleObjectProperty<Boolean>>
                addBooleanColumn(name, property)
            }
            String::class -> {
                property as KProperty1<T, SimpleObjectProperty<String>>
                addStringColumn(name, property)

            }
            else -> {
                if (AbstractEntity::class.java.isAssignableFrom(genericArgumentClass.java)) {
                    property as KProperty1<T, SimpleObjectProperty<AbstractEntity>>
                    addEntityColumn(name, property)
                } else
                    throw Exception("Unsupported argument type of object property")
            }
        }
    }

    private fun addIntegerColumn(name: String, property: KProperty1<T, ObjectProperty<Int>>) {
        editablePropertyHandlerList.add(Triple(PropertyHandler<T>::handleIntegerProperty, name, property))
        propertyColumnMap[property] = column(name, property)
    }

    private fun addLongColumn(name: String, property: KProperty1<T, ObjectProperty<Long>>) {
        editablePropertyHandlerList.add(Triple(PropertyHandler<T>::handleLongProperty, name, property))
        propertyColumnMap[property] = column(name, property)
    }

    private fun addStringColumn(name: String, property: KProperty1<T, ObjectProperty<String>>) {
        editablePropertyHandlerList.add(Triple(PropertyHandler<T>::handleStringProperty, name, property))
        propertyColumnMap[property] = column(name, property)
    }

    private fun addDateColumn(name: String, property: KProperty1<T, ObjectProperty<LocalDate>>) {
        editablePropertyHandlerList.add(Triple(PropertyHandler<T>::handleLocalDateProperty, name, property))
        propertyColumnMap[property] = column(name, property)
    }

    private fun addBooleanColumn(name: String, property: KProperty1<T, ObjectProperty<Boolean>>) {
        editablePropertyHandlerList.add(Triple(PropertyHandler<T>::handleBooleanProperty, name, property))
        propertyColumnMap[property] = column(name, property)
    }

    private fun addEntityColumn(
        name: String,
        property: KProperty1<T, SimpleObjectProperty<AbstractEntity>>
    ) {
        editablePropertyHandlerList.add(Triple(PropertyHandler<T>::handleEntityProperty, name, property))
        propertyColumnMap[property] = column(name, property)
    }


    private fun <S, T> TableView<S>.column(
        title: String,
        prop: KProperty1<S, ObservableValue<T>>,
        op: TableColumn<S, T>.() -> Unit = {}
    ): TableColumn<S, T> {
        val column = TableColumn<S, T>(title)
        column.cellValueFactory = Callback { prop.call(it.value) }
        addColumnInternal(column)
        return column.also(op)
    }

    fun getColumnBy(property: KProperty1<T, ObjectProperty<*>>): TableColumn<T, *>? {
        return propertyColumnMap[property]
    }

    fun getColumnNameBy(propertyName: String): String =
        propertyColumnMap.entries.first {
            it.key.name == propertyName
        }.value.text

    fun getPropertyBy(column: TableColumn<*, *>) =
        propertyColumnMap.entries.first { it.value == column }.key

    fun handleEditableProperties(handler: PropertyHandler<T>) {
        for ((function, name, property) in editablePropertyHandlerList) {
            if (isReadonly(property)) continue
            function.call(handler, name, property)
        }
    }

    interface PropertyHandler<T> {
        fun handleIntegerProperty(
            name: String, property: KProperty1<T, ObjectProperty<Int>>
        ) {
        }

        fun handleLongProperty(
            name: String, property: KProperty1<T, ObjectProperty<Long>>
        ) {
        }

        fun handleDoubleProperty(
            name: String, property: KProperty1<T, ObjectProperty<Double>>
        ) {
        }

        fun handleFloatProperty(
            name: String, property: KProperty1<T, ObjectProperty<Float>>
        ) {
        }

        fun handleBooleanProperty(
            name: String, property: KProperty1<T, ObjectProperty<Boolean>>
        ) {

        }

        fun handleStringProperty(
            name: String, property: KProperty1<T, ObjectProperty<String>>
        ) {
        }

        fun handleLocalDateProperty(
            name: String, property: KProperty1<T, ObjectProperty<LocalDate>>
        ) {
        }

        fun handleEntityProperty(
            name: String, property: KProperty1<T, ObjectProperty<AbstractEntity>>
        ) {
        }
    }


}

interface TableViewFactory {
    fun create(): EntityTableView<*>
}

class BrigadeTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Brigade>()) {
        addColumns("Название" to Brigade::name, "Должность рабочих" to Brigade::title)
        this
    }
}

class BrigadeMemberTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<BrigadeMember>()) {
        addColumns(
            "ФИО" to BrigadeMember::staff,
            "Начало работы" to BrigadeMember::startDate,
            "Конец работы" to BrigadeMember::finishDate,
            "Бригадир" to BrigadeMember::isBrigadier, "Бригада" to BrigadeMember::brigade
        )
        this
    }
}


class BuildObjectTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<BuildObject>()) {
        addColumns(
            "Номер" to BuildObject::id,
            "Начало строительства" to BuildObject::startDate,
            "Конец строительства" to BuildObject::finishDate,
            "Проект" to BuildObject::prototype,
            "Участок" to BuildObject::plot,
            "Заказчик" to BuildObject::customer
        )
        validator.setValidator<LocalDate>(
            BuildObject::startDate,
            "Дата окончания строительства >= дата начала строительства"
        ) { newValue, rowItem ->
            newValue ?: return@setValidator false
            rowItem.finishDate.value ?: return@setValidator true
            newValue <= rowItem.finishDate.value
        }
        validator.setValidator<LocalDate>(
            BuildObject::finishDate,
            "Дата окончания строительства >= дата начала строительства"
        ) { newValue, rowItem ->
            newValue ?: return@setValidator true
            rowItem.startDate.value ?: return@setValidator false
            newValue >= rowItem.startDate.value
        }
        validator.setValidator<Prototype>(BuildObject::prototype, "Значение не может быть пусто") { newValue, _ ->
            newValue ?: return@setValidator false
            true
        }
        validator.setValidator<Plot>(BuildObject::plot, "Значение не может быть пусто") { newValue, _ ->
            newValue ?: return@setValidator false
            true
        }
        validator.setValidator<Customer>(BuildObject::customer, "Значение не может быть пусто") { newValue, _ ->
            newValue ?: return@setValidator false
            true
        }
        setReadonly(BuildObject::id)


        this
    }
}

class CustomerTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Customer>()) {
        addColumns(
            "ФИО" to Customer::name,
            "Номер телефона" to Customer::phoneNumber
        )
        this
    }
}

class EstimateTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Estimate>()) {
        addColumns(
            "Количество" to Estimate::amount,
            "График работ" to Estimate::workSchedule,
            "Материал" to Estimate::material
        )
        this
    }
}

class MachineryTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Machinery>()) {
        addColumns(
            "Модель" to Machinery::model,
            "Строительное управление" to Machinery::management
        )
        this
    }
}

class ColumnUI(
    val name: String,
    val prop: KProperty1<*, *>,
    val sortName: String = name
)

class MachineryModelTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<MachineryModel>()) {
        addColumns("Название" to MachineryModel::name, "Тип" to MachineryModel::machineryType)
        this
    }
}

class MachineryTypeTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<MachineryType>()) {
        addColumns("Название" to MachineryType::name)
        this
    }
}

class ManagementTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Management>()) {
        addColumns("Номер" to Management::id, "Начальник" to Management::chief)
        this
    }
}

class MaterialTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Material>()) {
        addColumns("Название" to Material::name)
        this
    }
}

class MaterialConsumptionTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<MaterialConsumption>()) {
        addColumns(
            "Количество" to MaterialConsumption::amount,
            "Бригада-объект" to MaterialConsumption::objectBrigade,
            "Смета" to MaterialConsumption::estimate
        )
        this
    }
}

class ObjectBrigadeTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<ObjectBrigade>()) {
        addColumns(
            "Начало работы" to ObjectBrigade::startDate,
            "Конец работы" to ObjectBrigade::finishDate,
            "Бригада" to ObjectBrigade::brigade, "Объект" to ObjectBrigade::buildObject,
            "График работ" to
                    ObjectBrigade::workSchedule
        )
        this
    }
}

class ObjectMachineryTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<ObjectMachinery>()) {
        addColumns(
            "Начало работы" to ObjectMachinery::startDate,
            "Конец работы" to ObjectMachinery::finishDate,
            "Объект" to ObjectMachinery::buildObject,
            "Строительная техника" to ObjectMachinery::machinery
        )
        this
    }
}

class PlotTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Plot>()) {
        addColumns(
            "Номер" to Plot::id,
            "Начальник" to Plot::chief,
            "Строительное управление" to Plot::management
        )
        this
    }
}

class PrototypeTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Prototype>()) {
        addColumns(
            "Номер" to Prototype::id,
            "Срок строительства" to Prototype::deadline,
            "Вид" to Prototype::prototypeType
        )
        this
    }
}

class PrototypeTypeTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<PrototypeType>()) {
        addColumns("Название" to PrototypeType::name)
        this
    }
}

class StaffTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Staff>()) {
        addColumns(
            "ФИО" to Staff::name,
            "Номер телефона" to Staff::phoneNumber,
            "Должность" to Staff::title,
            "Начальник" to Staff::chief
        )
        this
    }
}

class TitleTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Title>()) {
        addColumns(
            "Название" to Title::name,
            "Категория" to Title::titleCategory
        )
        this
    }
}

class TitleCategoryTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<TitleCategory>()) {
        addColumns("Назавание" to TitleCategory::name)
        this
    }
}

class WorkScheduleTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<WorkSchedule>()) {
        addColumns(
            "Номер" to WorkSchedule::id,
            "Порядок выполнения" to WorkSchedule::ord,
            "Срок выполнения" to WorkSchedule::deadline,
            "Вид работ" to WorkSchedule::workType,
            "Проект" to WorkSchedule::prototype
        )
        this
    }
}

class WorkTypeTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<WorkType>()) {
        addColumns("Название" to WorkType::name)
        this
    }
}

class ReportTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<Report>()) {
        addColumns(
            "Бригада" to Report::brigade,
            "Объект" to Report::buildObject,
            "Вид работ" to Report::workType,
            "Начало работ" to Report::startDate,
            "Конец работ" to Report::finishDate,
            "Срок, дни" to Report::deadline,
            "Превышение сроков, дни" to Report::timeOverrun
        )
        this
    }
}


class MaterialConsumptionReportTableViewFactory : TableViewFactory {
    override fun create(): EntityTableView<*> = with(EntityTableView<MaterialConsumptionReport>()) {
        addColumns(
            "Отчет" to MaterialConsumptionReport::report,
            "Материал" to MaterialConsumptionReport::material,
            "Расход" to MaterialConsumptionReport::consAmount,
            "Смета" to MaterialConsumptionReport::estAmount,
            "Перерасход" to MaterialConsumptionReport::matConsOverrun
        )
        this
    }
}
