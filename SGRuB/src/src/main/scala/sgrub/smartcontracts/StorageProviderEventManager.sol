// SPDX-License-Identifier: GPL-3.0

pragma solidity ^0.8.2;

// client contract implements deliver callback function to receive requested values
contract StorageProviderEventManager {
    // a deliver event with indexed key and value to filter for
    event deliver(bytes8 indexed key, bytes proof);

    //owner of the contract
    address owner;

    // set the owner of this contract to whoever deployed it
    constructor() {
        owner = msg.sender;
    }

    // modifier for functions that can only be executed by the owner of the contract
    modifier onlyOwner() {
        require(msg.sender==owner, "Only the contract owner can call this function");
        _;
    }

    function emitDeliver(bytes8 key, bytes memory proof) public onlyOwner {
        emit deliver(key, proof);
    }
}
