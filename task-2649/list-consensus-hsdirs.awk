
BEGIN {
    OFS=":"
    idDigest="<oops>"
}

$1 == "r" {
    idDigest=$3
}

$1 == "s" {
    isHSDir=0
    for (i = NF; i >= 1; --i) {
        if ($i == "HSDir") {
            isHSDir=1
        }
    }

    if (isHSDir != 0) {
        print idDigest
    }

    idDigest="<oops>"
}

