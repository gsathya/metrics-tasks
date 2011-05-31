
set hsdir_set_name_list {}

array set hsdir_sets {}

proc read_file_lines {filename} {
    set chanid [open $filename r]
    set contents [read -nonewline $chanid]
    close $chanid

    return [split $contents "\n"]
}

proc get_hsdir_set_name {filename} {
    return [file tail $filename]
}

proc read_hsdir_set {set_name filename} {
    global hsdir_sets

    set hsdir_set [read_file_lines $filename]
    set hsdir_sets($set_name) $hsdir_set
}

proc forget_hsdir_set {name} {
    unset -nocomplain hsdir_sets($name)
}

proc list_set_symmetric_difference_size {l1 l2} {
    set l1 [lsort -ascii -unique $l1]
    set l2 [lsort -ascii -unique $l2]
    set both [lsort -ascii -unique [concat $l1 $l2]]

    set l1i 0
    set l2i 0
    #set result {}
    set result_length 0

    foreach x $both {
        set n_inputs_containing_x 0
        if {[lindex $l1 $l1i] eq $x} {
            incr l1i
            incr n_inputs_containing_x
        }
        if {[lindex $l2 $l2i] eq $x} {
            incr l2i
            incr n_inputs_containing_x
        }
        if {$n_inputs_containing_x & 1} {
            #lappend result $x
            incr result_length
        }
    }

    #return $result
    return $result_length
}
rename list_set_symmetric_difference_size lssds

while {![eof stdin]} {
    set x [gets stdin hsdir_set_filename]
    if {$x == -1} {break}

    set hsdir_set_name [get_hsdir_set_name $hsdir_set_filename]
    read_hsdir_set $hsdir_set_name $hsdir_set_filename

    lappend hsdir_set_name_list $hsdir_set_name

    if {[llength $hsdir_set_name_list] == 5} {
        lassign $hsdir_set_name_list set0 set1 set2 set3 set4

        puts "$set0\
              [llength $hsdir_sets($set0)]\
              [lssds $hsdir_sets($set0) $hsdir_sets($set1)]\
              [lssds $hsdir_sets($set0) $hsdir_sets($set2)]\
              [lssds $hsdir_sets($set0) $hsdir_sets($set3)]\
              [lssds $hsdir_sets($set0) $hsdir_sets($set4)]"

        forget_hsdir_set [lindex $hsdir_set_name_list 0]
        set hsdir_set_name_list [lrange $hsdir_set_name_list 1 end]
    }
}

