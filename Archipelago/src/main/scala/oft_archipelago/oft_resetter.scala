package oft_archipelago

import akka.actor.Actor

class resetter extends Actor{
  var nodes_finished_cleaning = 0
  var puppeteer_finished_cleaning = 0
  var max_register_finished_cleaning = 0
  var adopt_commit_maxes_finished_cleaning = 0
  def receive: PartialFunction[Any, Unit] = {
    case "node_reset" => {
      nodes_finished_cleaning += 1
      if(nodes_finished_cleaning == Main.N  && puppeteer_finished_cleaning == 1){
        nodes_finished_cleaning = 0
        puppeteer_finished_cleaning = 0
        max_register_finished_cleaning = 0
        adopt_commit_maxes_finished_cleaning = 0
        Main.new_round()
      }
    }
    case "puppeteer_reset" => {
      puppeteer_finished_cleaning += 1
      if(nodes_finished_cleaning == Main.N  && puppeteer_finished_cleaning == 1){
        nodes_finished_cleaning = 0
        puppeteer_finished_cleaning = 0
        max_register_finished_cleaning = 0
        adopt_commit_maxes_finished_cleaning = 0
        Main.new_round()
      }
    }
    case "max_register_reset" => {
      max_register_finished_cleaning += 1
      if(nodes_finished_cleaning == Main.N  && puppeteer_finished_cleaning == 1){
        println("all satisfied")
        nodes_finished_cleaning = 0
        puppeteer_finished_cleaning = 0
        max_register_finished_cleaning = 0
        adopt_commit_maxes_finished_cleaning = 0
        Main.new_round()
      }
    }
    case "adopt_commit_max_reset" => {
      adopt_commit_maxes_finished_cleaning += 1
      if(nodes_finished_cleaning == Main.N  && puppeteer_finished_cleaning == 1){
        println("all satisfied")
        nodes_finished_cleaning = 0
        puppeteer_finished_cleaning = 0
        max_register_finished_cleaning = 0
        adopt_commit_maxes_finished_cleaning = 0
        Main.new_round()
      }
    }
  }

}
