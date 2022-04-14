package util

object DefaultTransaction {

  def convert_data(actor: Int, data: String): String = {
   "act" + actor + data.slice(3, data.length)
  }
}