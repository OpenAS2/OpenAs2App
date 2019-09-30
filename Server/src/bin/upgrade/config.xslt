<xsl:stylesheet version="3.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
>
	<xsl:output method="xml" indent="yes" />
	<xsl:mode on-no-match="shallow-copy" />
	<xsl:template match="openas2/properties">
		<xsl:copy>
			<xsl:choose>
				<xsl:when test="not(@console.logger.enabled)">
					<xsl:attribute name="console.logger.enabled">
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@file.logger.enabled)">
					<xsl:attribute name="file.logger.enabled">
               <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@socket.logger.enabled)">
					<xsl:attribute name="socket.logger.enabled">
                <xsl:value-of select="'false'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@email.logger.enabled)">
					<xsl:attribute name="email.logger.enabled">
                    <xsl:value-of select="'false'" />
                </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@console.command.processor.enabled)">
					<xsl:attribute
						name="console.command.processor.enabled"
					>
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@stream.command.processor.enabled)">
					<xsl:attribute
						name="stream.command.processor.enabled"
					>
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@socket.command.processor.enabled)">
					<xsl:attribute
						name="socket.command.processor.enabled"
					>
                <xsl:value-of select="'false'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@module.AS2SenderModule.enabled)">
					<xsl:attribute name="module.AS2SenderModule.enabled">
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@module.MDNSenderModule.enabled)">
					<xsl:attribute name="module.MDNSenderModule.enabled">
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@module.DbTrackingModule.enabled)">
					<xsl:attribute name="module.DbTrackingModule.enabled">
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@module.MDNFileModule.enabled)">
					<xsl:attribute name="module.MDNFileModule.enabled">
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@module.MessageFileModule.enabled)">
					<xsl:attribute name="module.MessageFileModule.enabled">
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when
					test="not(@module.DirectoryResenderModule.enabled)"
				>
					<xsl:attribute
						name="module.DirectoryResenderModule.enabled"
					>
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when
					test="not(@module.AS2ReceiverModule.http.enabled)"
				>
					<xsl:attribute
						name="module.AS2ReceiverModule.http.enabled"
					>
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when
					test="not(@module.AS2MDNReceiverModule.http.enabled)"
				>
					<xsl:attribute
						name="module.AS2MDNReceiverModule.http.enabled"
					>
                <xsl:value-of select="'true'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when
					test="not(@module.AS2ReceiverModule.https.enabled)"
				>
					<xsl:attribute
						name="module.AS2ReceiverModule.https.enabled"
					>
                <xsl:value-of select="'false'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when
					test="not(@module.AS2MDNReceiverModule.https.enabled)"
				>
					<xsl:attribute
						name="module.AS2MDNReceiverModule.https.enabled"
					>
                <xsl:value-of select="'false'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:choose>
				<xsl:when test="not(@module.HealthCheckModule.enabled)">
					<xsl:attribute
						name="module.HealthCheckModule.enabled"
					>
                <xsl:value-of select="'false'" />
            </xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/loggers/logger[@classname='org.openas2.logging.ConsoleLogger' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.console.logger.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/loggers/logger[@classname='org.openas2.logging.FileLogger' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.file.logger.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/loggers/logger[@classname='org.openas2.logging.SocketLogger' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.socket.logger.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/loggers/logger[@classname='org.openas2.logging.EmailLogger' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.email.logger.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/commandProcessors/commandProcessor[@classname='org.openas2.cmd.processor.StreamCommandProcessor' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.stream.command.processor.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/commandProcessors/commandProcessor[@classname='org.openas2.cmd.processor.SocketCommandProcessor' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.socket.command.processor.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.sender.AS2SenderModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.AS2SenderModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.sender.MDNSenderModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.MDNSenderModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.msgtracking.DbTrackingModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.DbTrackingModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.storage.MessageFileModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.MessageFileModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.storage.MDNFileModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.MDNFileModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.receiver.HealthCheckModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.HealthCheckModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.receiver.AS2ReceiverModule' and not(@protocol) and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.AS2ReceiverModule.http.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.receiver.AS2ReceiverModule' and @protocol='https' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.AS2ReceiverModule.https.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.receiver.AS2MDNReceiverModule' and not(@protocol) and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.AS2MDNReceiverModule.http.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.receiver.AS2MDNReceiverModule' and @protocol='https' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.AS2MDNReceiverModule.https.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
	<xsl:template
		match="openas2/processor/module[@classname='org.openas2.processor.resender.DirectoryResenderModule' and not(@enabled)]"
	>
		<xsl:copy>
			<xsl:attribute name="enabled">
               <xsl:value-of
				select="'$properties.module.DirectoryResenderModule.enabled$'" />
            </xsl:attribute>
			<xsl:apply-templates select="node() | @*" />
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>