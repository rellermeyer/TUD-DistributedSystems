package nl.tudelft.IN4391G4.gui

import java.io.File
import java.nio.file.Files
import java.util.UUID

import javafx.embed.swing.JFXPanel
import nl.tudelft.IN4391G4.machines.{JavaJob, Job, JobContext}
import nl.tudelft.IN4391G4.messages.JobMessages.JobResult
import nl.tudelft.IN4391G4.messages.MachineState
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.text.Text
import scalafx.stage.{FileChooser, Stage}
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.Includes._
import scalafx.scene.{Node, Scene}
import scalafx.application.Platform
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import rx.subjects.PublishSubject

import scala.io.Source

class WorkstationUI(val machineName: String, val initialState: MachineState) {

  private var stateTextUI = new StringProperty(if(initialState == MachineState.Available) "Available" else "Busy") //property to hold the current workstation state text
  private var jobCountTextUI = new StringProperty("0/0") //property to hold the current workstation state text
  private var stateColorUI: Color = if(initialState == MachineState.Available) Color.CornflowerBlue else Color.LightSalmon

  private val jobPublisher: PublishSubject[(JobContext, Job)] = PublishSubject.create[(JobContext, Job)]
  val onSubmit: rx.Observable[(JobContext, Job)] = jobPublisher.asObservable()
  var initCompleted = false

  private var stage: Stage = _
  private var statusDisplayPane: Pane = _
  private var currentAlert: Alert = _

  private var submittedJobs: Int = 0
  private var completedJobs: Int = 0

  new JFXPanel() //initialises JFX

  Platform.runLater {
    stage = new Stage {
      outer =>
      title = s"Workstation - $machineName"
    }
    statusDisplayPane = createStatusDisplayPane()
    val borderPane = new BorderPane {
      top = statusDisplayPane
      center = createExecutableSelectPane(Color.DarkGrey, stage)
    }
    stage.scene = new Scene(640, 360) {
      root = borderPane
    }
    stage.show()
    initCompleted = true; //UI initialised, used for thread synchronisation
  }

  //PUBLIC method definitions
  def setState(state: MachineState): Unit = {
    Platform.runLater {
      stateTextUI.value = state.toString //update UI element
      stateColorUI = if(state == MachineState.Available) Color.CornflowerBlue else Color.LightSalmon
      statusDisplayPane.background = new Background(Array(new BackgroundFill(stateColorUI, null, null)))
    }
  }

  def newJobResult(res: JobResult): Unit = {
    completedJobs += 1
    Platform.runLater {
      updateJobCountUI()
      showNewJobResultAlert(stage, res)
    }
  }

  //PRIVATE method definitions
  private def newJobSubmission(id: UUID, fileBytes: Array[Byte], args: String): Unit = {
    //emit new jobcontext
    jobPublisher.onNext((JobContext(id, MachineState.Available), JavaJob(id, fileBytes, args)))
    submittedJobs += 1
    updateJobCountUI()
  }

  private def updateJobCountUI(): Unit = {
    jobCountTextUI.value = completedJobs+"/"+submittedJobs
  }

  private def createStatusDisplayPane():Pane = {

    val stateUIElem = new Text()
    stateUIElem.text <== stateTextUI

    //val statusText = stateUIElem
    val statusLabel = new Label("Status")
    statusLabel.labelFor = stateUIElem

    val jobCountUIElem = new Text()
    jobCountUIElem.text <== jobCountTextUI

    val jobCountLabel = new Label("Jobs completed/submitted")
    jobCountLabel.labelFor = jobCountUIElem

    makePane(List(statusLabel, stateUIElem, jobCountLabel, jobCountUIElem), stateColorUI)
  }

  private def createExecutableSelectPane(fill:Color, stage: Stage):Pane = {

    var selectedFile: File = null
    val fileChooser = new FileChooser {
      title = "Select Executable"
      extensionFilters += new ExtensionFilter("Jar Executables", "*.jar")
      extensionFilters += new ExtensionFilter("Bag of tasks", "*.bag")
    }

    val fileButton = new Button()
    fileButton.text <== fileChooser.title
    fileButton.onAction = _ => {
      selectedFile = fileChooser.showOpenDialog(stage)
      fileButton.text <== (if (selectedFile != null) new StringProperty(selectedFile.getName) else fileChooser.title)
    }
    val fileLabel = new Label("Select executable")
    fileLabel.labelFor = fileButton

    val cmdField = new TextField
    cmdField.text = ""
    val cmdLabel = new Label("Commandline expression")
    cmdLabel.labelFor = cmdField
    cmdLabel.margin = Insets(20, 0, 0, 0)

    val submitButton = new Button("Submit")
    submitButton.margin = cmdLabel.margin
    submitButton.onAction = _ => {
      if(selectedFile == null) {
        showMissingInputAlert(stage)
      } else {
        if(selectedFile.getName.toLowerCase().endsWith(".jar")){
          val fileBytes: Array[Byte] = Files.readAllBytes(selectedFile.toPath)
          newJobSubmission(UUID.randomUUID(), fileBytes, cmdField.text.value)
        } else if(selectedFile.getName.toLowerCase().endsWith(".bag")){
          onBagOfTasksRequest(selectedFile)
        }
      }
    }

    makePane(List(fileLabel, fileButton, cmdLabel, cmdField, submitButton), fill)
  }

  private def onBagOfTasksRequest(selectedFile: File): Unit ={
    val lines = Source.fromFile(selectedFile, "UTF-8").getLines
    for (line <- lines){
      line.split(';') match{
        case Array(bin, args) =>
          val fileBytes = Files.readAllBytes(new File(bin).toPath)
          newJobSubmission(UUID.randomUUID(), fileBytes, args)
        case Array(id, bin, args) =>
          val fileBytes = Files.readAllBytes(new File(bin).toPath)
          newJobSubmission(UUID.fromString(id), fileBytes, args)
        case _ => System.err.println("Unsupported file type.")
      }
    }
  }

  private def showMissingInputAlert(stage: Stage): Unit = {
    closeCurrentAlert()

    currentAlert = new Alert(AlertType.Warning) {
      initOwner(stage)
      title = "Warning"
      headerText = "Missing input"
      contentText = "Please select a *.jar or *.bag file and optionally provide commandline arguments."
    }
    currentAlert.show()
  }

  private def showNewJobResultAlert(stage: Stage, jobresult: JobResult): Unit = {
    closeCurrentAlert()

    val alertType = if(jobresult.exitCode > 0) AlertType.Error else AlertType.Information
    currentAlert = new Alert(alertType) {
      initOwner(stage)
      title = "Job completed"
      headerText = "Your job has exited with exitcode: " + jobresult.exitCode
      contentText = "Program output:\n" + jobresult.outputStream + "\nProgram error:\n" + jobresult.errorStream
    }
    currentAlert.show()
  }

  private def makePane(children: List[Node], fill: Color): Pane = {
    val pane = new VBox(5)
    pane.children = children
    pane.background = new Background(Array(new BackgroundFill(fill, null, null)))
    pane.padding = Insets(25)
    pane
  }

  private def closeCurrentAlert(): Unit = {
    if(currentAlert != null) {
      currentAlert.close()
    }
  }

}
