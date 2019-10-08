<template>
     <div id="editor">
        <form @submit="submitForm">
            <h1 v-html="title"></h1>
                <input type="hidden" v-model="data.data" name="data"  >
                <div class="form-group" >
                        <label for="alias">Certificate Alias Name</label>
                        <input type="text" v-model="data.alias" name="alias"  class="form-control" >
                </div>
                <div class="form-group " >
                    <label for="serial" >Serial Number (HEX)</label>
                    <div class="form-control" name="serial" v-html="serial"></div>
                </div>
                <div class="form-group " >
                    <label for="validNotBefore" >Valid Not Before</label>
                    <div class="form-control" name="validNotBefore" v-html="validNotBefore"></div>
                </div>
                <div class="form-group " >
                    <label for="validNotAfter" >Expiration Date</label>
                    <div class="form-control" name="validNotAfter" v-html="validNotAfter"></div>
                </div>
                <div class="form-group " >
                    <label for="subject" >Subject</label>
                    <div class="form-control" name="subject" v-html="subject"></div>
                </div>
                <div class="form-group " >
                    <label for="fingerPrint" >Fingerprint (SHA1)</label>
                    <div class="form-control" name="fingerPrint" v-html="fingerPrint"></div>
                </div>
                <div class="form-group">
                    <label for="upload">Upload Certificate</label>
                    <input type="file" id="upload" name="upload" @change="fileUpload">
                </div>
                <div class="btn-group" role="group" aria-label="Actions">
                    <button type="submit" class="btn btn-primary">Ok</button>
                    <a type="button" @click="cancelForm" class="btn btn-outline-secondary">Cancel</a>
                    <a type="button" v-if="data._id !== 'new'" @click="deleteForm" class="btn btn-danger">Delete</a>
                </div>
        </form>
     </div>
</template>
<script>
export default {
    data:function() {
        return  {
            serial:0,
            validNotBefore: null,
            validNotAfter: null,
            fingerPrint: '',
            subject: '',
            issuerName: '',
            data: {alias:null, data:null}
        };
    },
     props: {
        model: {
            type: Promise,
            required: true
        },
        schema: {
            type: Object,
            required: true
        },
        title: {
            type: String,
            required: false
        }
    },
    watch: {
        data: function(e) {
            console.log('DATA CHANGED!',e);
            this.changedCert();
        }
    },
    mounted: function() {
        console.log(this.model);
        this.model.then((e)=>this.data=e);
    },
    methods: {
        submitForm: function(e) {
            console.log('submitForm',e);
            e.preventDefault();
            this.$emit('commit',this.data);
        },
        deleteForm: function(e) {
            console.log('submitForm',e);
            e.preventDefault();
            this.$emit('delete',this.data._id);
        },
        cancelForm: function(e) {
            this.$emit('revert',this.data._id);
        },
        fileUpload: function(e) {
          var oFile = document.getElementById('upload').files[0];
          console.log(oFile,e);
          // filter for image files
          var rFilter = /^(application\/x-x509-ca-cert|application\/x-pkcs12)$/i;
          if (! rFilter.test(oFile.type)) {
              alert('Error invalid file-type')
              return;
          }
          if (oFile.size == 0) {
              alert('Error unable to read file, empty?');
              return;
          } 
          var oReader = new FileReader();
          var that=this;
          oReader.onload = function(e){
            console.log(e.target.result);
            var PEM = e.target.result;
            PEM=PEM.replace('-----BEGIN CERTIFICATE-----','').replace('-----END CERTIFICATE-----','');
            PEM=PEM.replace(/[^A-Za-z0-9+/=]+/gm,'');
            that.data.data = PEM;
            that.changedCert();
          }
          oReader.readAsText(oFile);

        },
        changedCert: function() {
            var cert = new X509();
            var PEM = this.data.data;
            if(this.data.data == null) {
                this.serial = '';
                this.issuerName='';
                this.validNotBefore = null;
                this.validNotAfter = null;
                this.subject = '';
                this.fingerPrint = '';
            return;
            }
            if(PEM.substr(0,4) !== '----'){
                PEM = '-----BEGIN CERTIFICATE-----\n' + PEM + '\n-----END CERTIFICATE-----';
            }
            cert.readCertPEM(PEM);
            this.serial = cert.getSerialNumberHex();
            this.issuerName=cert.getIssuerString();
            this.validNotBefore = zulutodate(cert.getNotBefore());
            this.validNotAfter = zulutodate(cert.getNotAfter());
            this.subject = cert.getSubjectString();
            this.fingerPrint = KJUR.crypto.Util.hashHex(cert.hex, 'sha1');
            console.log(cert);
        }
    }
}
</script>
<style scoped>

</style>