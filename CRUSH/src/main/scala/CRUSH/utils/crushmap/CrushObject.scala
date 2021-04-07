package CRUSH.utils.crushmap

import CRUSH.CBorSerializable

/**
 *
 * @param hash   Hash of the object name.
 * @param size   Size of the object.
 * @param value  Name of the object.
 */
case class CrushObject(val hash: Int, var size: Int, var value: String) extends CBorSerializable
