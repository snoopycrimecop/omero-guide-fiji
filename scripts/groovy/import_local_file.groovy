/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2019-2020 University of Dundee. All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 */

/*
 * This Groovy script imports a local file to OMERO.
 * The user selects a file to import.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

#@ String(label="Username") USERNAME
#@ String(label="Password", style='password') PASSWORD
#@ String(label="Host", value='wss://workshop.openmicroscopy.org/omero-ws') HOST

import java.io.File
import java.lang.reflect.Array

// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import omero.gateway.model.DatasetData
import omero.log.SimpleLogger

import ome.formats.importer.ImportConfig
import ome.formats.importer.OMEROWrapper
import ome.formats.importer.ImportLibrary
import ome.formats.importer.ImportCandidates
import ome.formats.importer.cli.ErrorHandler
import ome.formats.importer.cli.LoggingImportMonitor

import loci.formats.in.DefaultMetadataOptions
import loci.formats.in.MetadataLevel

import ij.IJ

def connect_to_omero() {
    "Connect to OMERO"

    credentials = new LoginCredentials()
    credentials.getServer().setHostname(HOST)
    credentials.getUser().setUsername(USERNAME.trim())
    credentials.getUser().setPassword(PASSWORD.trim())
    simpleLogger = new SimpleLogger()
    gateway = new Gateway(simpleLogger)
    gateway.connect(credentials)
    return gateway

}

def find_dataset(gateway, dataset_id) {
    "Load the Dataset"
    browse = gateway.getFacility(BrowseFacility)
    user = gateway.getLoggedInUser()
    ctx = new SecurityContext(user.getGroupId())
    return browse.findIObject(ctx, "omero.model.Dataset", dataset_id)
}

def upload_image(paths, gateway, id) {
    "Upload an image to OMERO"

    user = gateway.getLoggedInUser()
    sessionKey = gateway.getSessionId(user)

    config = new ImportConfig()
    config.debug.set('false')
    config.hostname.set(HOST)
    config.port.set(443)
    config.sessionKey.set(sessionKey)
    dataset = find_dataset(gateway, id)

    store = config.createStore()
    reader = new OMEROWrapper(config)

    library = new ImportLibrary(store, reader)
    error_handler = new ErrorHandler(config)

    library.addObserver(new LoggingImportMonitor())
    candidates = new ImportCandidates(reader, paths, error_handler)
    containers = candidates.getContainers()
    containers.each() { c ->
        c.setTarget(dataset)
    }
    reader.setMetadataOptions(new DefaultMetadataOptions(MetadataLevel.ALL))
    return library.importCandidates(config, candidates)
}

path_to_file = IJ.getFilePath("Choose a File")
if (path_to_file == null) {
	println "cancel"
	return
}

// Connect to OMERO
gateway = connect_to_omero()


// Create a Dataset
d = new DatasetData()
d.setName("Image Imported via Fiji")
dm = gateway.getFacility(DataManagerFacility)
user = gateway.getLoggedInUser()
ctx = new SecurityContext(user.getGroupId())
d = dm.createDataset(ctx, d, null)

print d.getId()
// Import the selected image to OMERO
println "importing..."
str2d = new String[1]
str2d[0] = path_to_file
success = upload_image(str2d, gateway, d.getId())

gateway.disconnect()

println("Done")

