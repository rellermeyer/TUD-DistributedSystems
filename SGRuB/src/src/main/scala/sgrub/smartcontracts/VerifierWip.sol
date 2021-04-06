// SPDX-License-Identifier: GPL-3.0

pragma solidity >=0.8.2 <0.9.0;
pragma abicoder v2;
pragma experimental ABIEncoderV2;

/**
 * @title VerifierWip
 * @dev WIP: Attempting to perform lookup verification in Solidity
 */
contract VerifierWip {

	event VerifiedOutput (
		bool valid,
		bytes value
		);

	uint256 number;

	bytes32 latestDigest;
	uint8 rootNodeHeight;

	/**
     * @dev Store value in variable
     * @param num value to store
     */
	function store(uint256 num) public {
		number = num;
	}

	/**
     * @dev Return value
     * @return value of 'number'
     */
	function retrieve() public view returns (uint256){
		return number;
	}

	function updateDigest(bytes memory digest) public {
		require(digest.length == 33, "Digest must be 33 bytes");
		latestDigest = toLabel(digest, 0);
		rootNodeHeight = toUint8(digest, 32);
	}

	function verify(bytes8 key, bytes memory proof) public payable {
		// keyLength = 8
		uint16 maxNodes = 2 * uint16(rootNodeHeight) + 2;
		uint16 numNodes = 0;
		uint8 labelsHead = 0;
		bytes32[4] memory labels;
		uint256 i = 0;
		bool valueExists = false;
		bytes memory value;
		while (toInt8(proof, i) != 4 && i < proof.length) {
			int8 n = toInt8(proof, i);
			i += 1;
			numNodes += 1;

			if (numNodes > maxNodes) {
				emit VerifiedOutput(false, "");
				return;
			}

			if (n == 3) { // Label
//				labels[labelsHead] = toLabel(proof, i);
//				labelsHead += 1;
				i += 32;
			}
			else if (n == 2) { // Leaf
				if (valueExists) {
					emit VerifiedOutput(false, "");
					return;
				}
				bytes8 leafKey = toLeafKey(proof, i);
				i += 8;
				if (leafKey != key) {
					emit VerifiedOutput(true, bytes8ToBytes(leafKey));
					return;
				}
				bytes8 nextLeafKey = toLeafKey(proof, i);
				i += 8;
				int32 valueLength = toInt32(proof, i);
				i += 4;
				value = new bytes(uint32(valueLength));
				assembly {
					value := mload(add(proof, i))
				}
				i += uint32(valueLength);
				valueExists = true;
				labels[labelsHead] = keccak256(abi.encodePacked(int8(0), leafKey, value, nextLeafKey));
				labelsHead += 1;
				// Temp:
				emit VerifiedOutput(true, value);
				return;
			}
			else { // Internal node
//				bytes32 labelLeft = labels[labelsHead - 1];
//				labelsHead -= 1;
//				bytes32 labelRight = labels[labelsHead - 1];
//				labelsHead -= 1;
//				labels[labelsHead] = keccak256(abi.encodePacked(int8(1), n, labelLeft, labelRight));
//				labelsHead += 1;
			}
		}
		if (labelsHead == 1) {
			if (labels[0] == latestDigest) {
				emit VerifiedOutput(true, value);
				return;
			}
		}

		emit VerifiedOutput(false, "");
	}

	function toLeafKey(bytes memory input, uint offset) private pure returns(bytes8 output) {
		assembly {
			output := mload(add(input, offset))
		}
	}

	function toLabel(bytes memory input, uint offset) private pure returns(bytes32 output) {
		assembly {
			output := mload(add(input, offset))
		}
	}

	function toInt8(bytes memory input, uint offset) private pure returns (int8 output) {
		assembly {
			output := mload(add(input, offset))
		}
	}

	function toUint8(bytes memory input, uint offset) private pure returns (uint8 output) {
		assembly {
			output := mload(add(input, offset))
		}
	}

	function toInt32(bytes memory input, uint offset) private pure returns (int32 output) {
		assembly {
			output := mload(add(input, offset))
		}
	}

	function bytes8ToBytes(bytes8 _bytes8) public pure returns (bytes memory) {
		uint8 i = 0;
		bytes memory bytesArray = new bytes(8);
		for (i = 0; i < 8; i++) {
			bytesArray[i] = _bytes8[i];
		}
		return bytesArray;
	}
}