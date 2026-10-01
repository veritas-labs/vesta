package uk.veritaslabs.vesta.devicesync

/** The steps [DeviceSyncService.pairWith] runs, in order, to add another device to this device's group. */
enum class PairingStep { PrepareGroup, CreateInvite, SendInvite, AwaitJoin, GrantAdmin, SaveDevice }

enum class PairingStepStatus { Pending, Running, Done, Failed }

/** Called as [PairingStep]s start, finish or fail. */
typealias PairingProgressListener = (PairingStep, PairingStepStatus) -> Unit
