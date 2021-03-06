SUMMARY = "Recipe to generate necessary artifacts to use fpga-manager"
DESCRIPTION = "This recipe generates bin and dtbo files to load/unload overlays using fpga-manager-script"

LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://xadcps/data/xadcps.mdd;md5=f7fa1bfdaf99c7182fc0d8e7fd28e04a"

inherit deploy xsctbase xsctyaml

REPO ??= "git://github.com/xilinx/device-tree-xlnx.git;protocol=https"
BRANCH ??= "master"
BRANCHARG = "${@['nobranch=1', 'branch=${BRANCH}'][d.getVar('BRANCH', True) != '']}"
SRC_URI = "${REPO};${BRANCHARG}"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

SRCREV ??= "a8b39cf536e6ccda56affb27b2727c1e4d6edad2"
PV = "xilinx+git${SRCPV}"

FILESEXTRAPATHS_append := ":${XLNX_SCRIPTS_DIR}"

SRC_URI_append = " \
        file://multipleHDF.tcl \
        file://base-hsi.tcl \
        "
DEPENDS += "\
    virtual/hdf \
    virtual/bitstream \
    virtual/dtb \
    dtc-native \
"

PACKAGE_ARCH ?= "${MACHINE_ARCH}"
COMPATIBLE_MACHINE ?= "^$"
COMPATIBLE_MACHINE_zynqmp = ".*"
COMPATIBLE_MACHINE_zynq = ".*"

XSCTH_SCRIPT = "${WORKDIR}/multipleHDF.tcl"
XSCTH_BUILD_CONFIG ?= 'Release'

DTS_INCLUDE ?= "${WORKDIR}"
DT_PADDING_SIZE ?= "0x1000"

DEVICETREE_FLAGS ?= " \
                -R 8 -p ${DT_PADDING_SIZE} -b 0 -@ -H epapr \
                ${@' '.join(['-i %s' % i for i in d.getVar('DTS_INCLUDE', True).split()])} \
               "
DEVICETREE_PP_FLAGS ?= " \
                -nostdinc -Ulinux -x assembler-with-cpp \
                ${@' '.join(['-I%s' % i for i in d.getVar('DTS_INCLUDE', True).split()])} \
                "
HDF_EXT ?= "xsa"
EXTRA_HDF ?= ""
XSCTH_HDF ?= "${WORKDIR}${EXTRA_HDF}"
XSCTH_MISC = " -hdf_type ${HDF_EXT}"
HDF_LIST = ""

YAML_OVERLAY_CUSTOM_DTS = "pl-final.dts"

do_fetch[cleandirs] = "${XSCTH_HDF}"
do_configure[cleandirs] = "${XSCTH_WS}"

do_configure_append () {
    for hdf in ${HDF_LIST}; do
        customfile=${WORKDIR}${EXTRA_HDF}/${hdf}.dtsi
        if [ -f "${customfile}" ];then
            echo "Using pl-custom.dtsi from: ${EXTRA_HDF}/${hdf}.dtsi"
            cp ${customfile} ${XSCTH_WS}/${hdf}/pl-custom.dtsi
        fi
    done
}


do_compile() {

        for hdf in ${HDF_LIST}; do

                #generate .dtbo
                DTS_FILE=${XSCTH_WS}/${hdf}/pl-final.dts
                #use the existance of the '/plugin/' tag to detect overlays
                #checking pl.dtsi but compiling pl-final.dts as pl-final.dts just includes
                #both pl.dtsi and pl-custom.dtsi
                if grep -qse "/plugin/;" ${XSCTH_WS}/${hdf}/pl.dtsi; then
                        ${BUILD_CPP} ${DEVICETREE_PP_FLAGS} -o ${hdf}-pl-final.dts.pp ${DTS_FILE}
                        dtc ${DEVICETREE_FLAGS} -I dts -O dtb -o ${hdf}.dtbo ${hdf}-pl-final.dts.pp
                else
                        #not an error
                        echo "${DTS_FILE} is not an overlay!"
                fi

                #generate .bin
                BIT=`ls ${XSCTH_WS}/${hdf}/*.bit`
                bitname=`basename ${BIT}`
                printf "all:\n{\n\t${BIT}\n}" > ${hdf}.bif
                bootgen -image ${hdf}.bif -arch ${SOC_FAMILY} -o ${bitname}.bin_${hdf} -w on ${@bb.utils.contains('SOC_FAMILY','zynqmp','','-process_bitstream bin',d)}

                #need this as with -process_bitstream flag bin file is automatically created in same dir as bitstream
                if [ "${SOC_FAMILY}" = "zynq" ]; then
                        cp ${XSCTH_WS}/${hdf}/*.bit.bin ./${bitname}.bin_${hdf}
                fi

                if [ ! -e "${bitname}.bin_${hdf}" ]; then
                        bbfatal "bootgen failed. Enable -log debug with bootgen and check logs"
                fi
        done

        #generate bin file for base hdf and copy over dtb file
        if [ ! -e "${RECIPE_SYSROOT}/boot/devicetree/pl-final.dtbo" ]; then
                echo "base dtbo was not generated.  Either base design has no pl.dtsi or dtbo was not generated. Please check logs if dtbo was expected"
        else
                cp ${RECIPE_SYSROOT}/boot/devicetree/pl-final.dtbo ${XSCTH_WS}/base.dtbo

                basebit=`ls ${RECIPE_SYSROOT}/boot/bitstream/*`
                bitname=`basename $basebit`
                printf "all:\n{\n\t${basebit}\n}" > base.bif
                bootgen -image base.bif -arch ${SOC_FAMILY} -o ${bitname}.bin_base -w on ${@bb.utils.contains('SOC_FAMILY','zynqmp','','-process_bitstream bin',d)}

                #need this as with -process_bitstream flag bin file is automatically created in same dir as bitstream
                if [ "${SOC_FAMILY}" = "zynq" ]; then
                        cp ${RECIPE_SYSROOT}/boot/bitstream/*.bit.bin ./${bitname}.bin_base
                fi

                if [ ! -e "${bitname}.bin_base" ]; then
                        bbfatal "bootgen failed. Enable -log debug with bootgen and check logs"
                fi
        fi
}

do_install() {
        install -d ${D}/lib/firmware/base
        if [ -e "base.dtbo" ]; then
            #install base hdf artifacts
            install -Dm 0644 base.dtbo ${D}/lib/firmware/base/base.dtbo
            newname=`basename *.bit.bin_base | awk -F '.bit.bin_' '{print $1}'`
            install -Dm 0644 *.bit.bin_base ${D}/lib/firmware/base/${newname}.bit.bin
        fi
        for hdf in ${HDF_LIST}; do
                install -Dm 0644 ${hdf}.dtbo ${D}/lib/firmware/${hdf}/${hdf}.dtbo
                newname=`basename *.bit.bin_${hdf} | awk -F '.bit.bin_' '{print $1}'`
                install -Dm 0644 *.bit.bin_${hdf} ${D}/lib/firmware/${hdf}/${newname}.bit.bin
        done
}

ALLOW_EMPTY_${PN} = "1"

python () {
        if d.getVar('FPGA_MNGR_RECONFIG_ENABLE') == '1':
                extra = d.getVar('EXTRA_HDF', True)
                pn = d.getVar('PN')
                baselib = d.getVar('base_libdir')
                packages = d.getVar('PACKAGES').split()

                #package base hdf
                packages.append(pn + '-base')
                d.setVar('FILES_' + pn + '-base', baselib + '/firmware/base')
                d.setVar('PACKAGES', ' '.join(packages))
                d.setVar('RDEPENDS_' + pn , pn + '-base')

                if extra:
                        hdflist = []
                        hdffullpath = []
                        import glob
                        for hdf in glob.glob(d.getVar('EXTRA_HDF', True)+"/*." + d.getVar('HDF_EXT')):
                                name = os.path.splitext(os.path.basename(hdf))[0]
                                hdflist.append(name)
                                hdffullpath.append(hdf)
                                dtsifile = d.getVar('EXTRA_HDF', True) + "/" + name + ".dtsi"
                                if os.path.isfile(dtsifile):
                                    hdffullpath.append(dtsifile)

                                d.setVar('FILES_' + pn + '-' + name, baselib + '/firmware/' + name )
                        d.setVar('HDF_LIST', ' '.join(hdflist))
                        extrapackages = [pn + '-{0}'.format(i) for i in hdflist]
                        packages = packages + extrapackages
                        d.setVar('PACKAGES', ' '.join(packages))
                        #Add all extra hdfs to src_uri
                        d.setVar('SRC_URI', ' '.join([' file://{0}'.format(i) for i in hdffullpath] + d.getVar('SRC_URI').split()))

                        #put back base package when setting RDEPENDS
                        extrapackages.append(pn + '-base')
                        d.setVar('RDEPENDS_'+pn , ' '.join(extrapackages))
}
