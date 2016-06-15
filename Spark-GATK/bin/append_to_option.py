#!/usr/bin/env python
#
# Copyright (c) 2016 NCIC, Institute of Computing Technology, Chinese Academy of Sciences
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#


"""Append text to an option.

Usage:

    append_to_option.py DELIMITER OPTION APPEND_STRING OPTION_STRING

For example, running

    append_to_option.py , --jars myproject.jar --option1 value1 --jars otherproject.jar --option2 value2

will write to stdout

    --option1 value1 --jars otherproject.jar,myproject.jar --option2 value2
"""

import sys

delimiter = sys.argv[1]
target = sys.argv[2]
append = sys.argv[3]
original = sys.argv[4:]

if original.count(target) > 1:
    sys.stderr.write("Found multiple %s in the option list." % target)
    sys.exit(1)

if original.count(target) == 0:
    original.extend([target, append])
else:  # original.count(target) == 1
    idx = original.index(target)
    new_value = delimiter.join([original[idx + 1], append])
    original[idx + 1] = new_value
sys.stdout.write(' '.join(original))
