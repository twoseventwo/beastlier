/*
* File ClinicalCase.java
*
* Copyright (C) 2016 Matthew Hall mdhall@ic.ac.uk
*
* This file is part of BEASTLIER, a BEAST 2 package.
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this program; if not, write to the
* Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
* Boston, MA  02110-1301  USA
*/

package beastlier.outbreak;

import beast.core.Description;
import beast.core.Input;
import beast.core.parameter.RealParameter;

/**
 *@author Matthew Hall <mdhall@ic.ac.uk>
        */

@Description("A ClinicalCase with a longitude and latitude")
public class GeographicallyLocatedClinicalCase extends ClinicalCase {

    public Input<RealParameter> longitudeInput = new Input<>("longitude", "Longitude (x) component of this host's" +
            " location");
    public Input<RealParameter> latitudeInput = new Input<>("latitude", "Latitude (y) component of this host's" +
            " location");

    private RealParameter longitude;
    private RealParameter latitude;

    public void initAndValidate(){
        super.initAndValidate();
        longitude = longitudeInput.get();
        latitude = latitudeInput.get();
    }

    public double[] getCoordinates(){
        double[] out = {longitude.getValue(), latitude.getValue()};
        return out;
    }

}
