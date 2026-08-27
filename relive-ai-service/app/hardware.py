import os
import psutil
import torch


class Tier:
    LOW = "low"
    MID = "mid"
    HIGH = "high"


RAM_SAFETY_MARGIN_GB = 2.0
VRAM_SAFETY_MARGIN_GB = 1.0

FORCE_TIER = os.environ.get("RELIVE_FORCE_TIER")  # "low" | "mid" | "high" | None


def get_available_ram_gb() -> float:
    return psutil.virtual_memory().total / (1024 ** 3)


def get_available_vram_gb() -> float:
    if not torch.cuda.is_available():
        return 0.0
    try:
        props = torch.cuda.get_device_properties(0)
        return props.total_memory / (1024 ** 3)
    except Exception:
        return 0.0


def has_gpu() -> bool:
    return torch.cuda.is_available()


def cpu_ram_tier() -> str:
    if FORCE_TIER:
        return FORCE_TIER

    ram = get_available_ram_gb()
    if ram >= 14.0:
        return Tier.MID
    return Tier.LOW


def gpu_tier() -> str:
    if FORCE_TIER:
        return FORCE_TIER

    if not has_gpu():
        return cpu_ram_tier()

    vram = get_available_vram_gb()
    if vram >= 16.0:
        return Tier.HIGH
    if vram >= 6.0:
        return Tier.MID
    return Tier.LOW


def describe() -> str:
    ram = get_available_ram_gb()
    vram = get_available_vram_gb()
    gpu = "yes" if has_gpu() else "no"
    return (
        f"RAM={ram:.1f}GB, GPU={gpu}, VRAM={vram:.1f}GB, "
        f"cpu_ram_tier={cpu_ram_tier()}, gpu_tier={gpu_tier()}"
    )