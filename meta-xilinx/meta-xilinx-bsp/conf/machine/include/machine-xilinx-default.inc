# Default Xilinx BSP Machine settings

MACHINE_FEATURES_BACKFILL_CONSIDERED += "rtc"

# File System Configuration
IMAGE_FSTYPES ?= "tar.gz cpio cpio.gz.u-boot"

# Kernel Configuration
XILINX_DEFAULT_KERNEL := "linux-xlnx"
XILINX_DEFAULT_KERNEL_microblaze := "linux-yocto"
XILINX_DEFAULT_KERNEL_zynqmp := "linux-yocto"
PREFERRED_PROVIDER_virtual/kernel ??= "${XILINX_DEFAULT_KERNEL}"

# U-Boot Configuration
XILINX_DEFAULT_UBOOT := "u-boot-xlnx"
XILINX_DEFAULT_UBOOT_zynqmp := "u-boot"
PREFERRED_PROVIDER_virtual/bootloader ??= "${XILINX_DEFAULT_UBOOT}"
PREFERRED_PROVIDER_virtual/boot-bin ??= "${PREFERRED_PROVIDER_virtual/bootloader}"

WIC_DEPENDS ?= "virtual/kernel virtual/bootloader virtual/boot-bin virtual/arm-trusted-firmware"
WIC_DEPENDS_zynq = "virtual/kernel virtual/bootloader virtual/boot-bin"
do_image_wic[depends] += "${@' '.join('%s:do_deploy' % r for r in d.getVar('WIC_DEPENDS').split())}"

UBOOT_SUFFIX ?= "img"
UBOOT_SUFFIX_zynqmp ?= "bin"
UBOOT_SUFFIX_microblaze ?= "bin"

UBOOT_BINARY ?= "u-boot.${UBOOT_SUFFIX}"
UBOOT_ELF ?= "u-boot"
UBOOT_ELF_zynq ?= "u-boot.elf"
UBOOT_ELF_aarch64 ?= "u-boot.elf"
UBOOT_ELF_versal ?= "u-boot.elf"

#Hardware accelaration
PREFERRED_PROVIDER_virtual/libgles1_mali400 = "libmali-xlnx"
PREFERRED_PROVIDER_virtual/libgles2_mali400 = "libmali-xlnx"
PREFERRED_PROVIDER_virtual/egl_mali400 = "libmali-xlnx"
PREFERRED_PROVIDER_virtual/libgl_mali400 = "mesa-gl"
PREFERRED_PROVIDER_virtual/mesa_mali400 = "mesa-gl"

# microblaze does not get on with pie for reasons not looked into as yet
GCCPIE_microblaze = ""
GLIBCPIE_microblaze = ""
SECURITY_CFLAGS_microblaze = ""
SECURITY_LDFLAGS_microblaze = ""

XSERVER ?= " \
	xserver-xorg \
	xf86-input-evdev \
	xf86-input-mouse \
	xf86-input-keyboard \
	xf86-video-fbdev \
	${XSERVER_EXT} \
	"

IMAGE_BOOT_FILES ?= "${@get_default_image_boot_files(d)}"

def get_default_image_boot_files(d):
    files = []

    # kernel images
    kerneltypes = set((d.getVar("KERNEL_IMAGETYPE") or "").split())
    kerneltypes |= set((d.getVar("KERNEL_IMAGETYPES") or "").split())
    for i in kerneltypes:
        files.append(i)

    # u-boot image
    if d.getVar("UBOOT_BINARY"):
        files.append(d.getVar("UBOOT_BINARY"))

    # device trees (device-tree only), these are first as they are likely desired over the kernel ones
    if "device-tree" in (d.getVar("MACHINE_ESSENTIAL_EXTRA_RDEPENDS") or ""):
        files.append("devicetree/*.dtb")


    # device trees (kernel only)
    if d.getVar("KERNEL_DEVICETREE"):
        dtbs = d.getVar("KERNEL_DEVICETREE").split(" ")
        dtbs = [os.path.basename(d) for d in dtbs]
        for dtb in dtbs:
            files.append(dtb)

    return " ".join(files)

XSERVER_EXT ?= ""
XSERVER_EXT_zynqmp ?= "xf86-video-armsoc"

#Add preferred provider as qemu-xilinx-helper-native
PREFERRED_PROVIDER_qemu-helper-native = "qemu-xilinx-helper-native"

# For MicroBlaze default all microblaze machines to use GDB 7.7.1 (for gdbserver/gdb)
PREFERRED_VERSION_gdb_microblaze = "7.7.1"

FPGA_MNGR_RECONFIG_ENABLE ?= "${@bb.utils.contains('IMAGE_FEATURES', 'fpga-manager', '1', '0', d)}"