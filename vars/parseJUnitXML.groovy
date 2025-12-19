#!/usr/bin/env groovy

def parseJUnitXML(xmlFile) {
    def testResults = [
        total: 0,
        passed: 0,
        failed: 0,
        duration: 0,
        failedTests: ''
    ]
    
    try {
        def xmlContent = readFile(file: xmlFile)
        def testsuites = new XmlSlurper().parseText(xmlContent)
        
        testsuites.testsuite.each { testsuite ->
            // Use .text() to get attribute values as strings
            testResults.total += testsuite.'@tests'.text() as Integer
            testResults.failed += testsuite.'@failures'.text() as Integer
            testResults.duration += testsuite.'@time'.text() as Double
            
            // Collect failed test details
            testsuite.testcase.each { testcase ->
                if (testcase.failure.size() > 0) {
                    def testName = testcase.'@name'.text()
                    def failureMessage = testcase.failure.text()
                    testResults.failedTests += "${testName}: ${failureMessage}\n"
                }
            }
        }
        
        testResults.passed = testResults.total - testResults.failed
        testResults.duration = String.format("%.1f", testResults.duration)
        
        // Trim trailing newline from failedTests
        if (testResults.failedTests) {
            testResults.failedTests = testResults.failedTests.trim()
        }
        
    } catch (Exception e) {
        echo "Error parsing JUnit XML: ${e.message}"
        testResults.failedTests = "Error parsing test results: ${e.message}"
    }
    
    return testResults
}
