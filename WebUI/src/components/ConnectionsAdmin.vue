<template>
    <div id="connections">
        <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
        <h1 class="h2">Connections</h1>
        <div class="btn-toolbar mb-2 mb-md-0">
          <div class="btn-group mr-2">
            <button type="button"  @click="$router.push({ name: 'ConnectionEditor',params: { id: 'new'} })" class="btn btn-sm btn-outline-secondary">New</button>
          </div>
        </div>
      </div>
      <object-editor v-if="$route.params.id" 
            title="Connection Editor"
            :schema="schema"
            :model="getObject($route.params.id)"
            @commit="saveObject" 
            @delete="(id)=>this.deleteObject(id)" 
            @revert="revert">
            
        </object-editor>
        <div v-else>
            <object-list :src="getList"></object-list>
        </div>
    </div>
</template>
<script>
import ObjectList from './ObjectList';
import ObjectEditor from './ObjectEditor';
import Utils from '../utils';
export default {
  data: function() {
        return {
            schema: { 
                fields: [
                    { 
                        type: "text",
                        name: "name",
                        label: "Partnership ID",
                        placeholder: "Connection's Unique Key Name",
                        value: ""
                    },
                    { 
                        type: "select",
                        name: "receiverIDs",
                        label: "Receiving Partner",
                        placeholder: "Connection's Receiver",
                        list: [],
                        value: ""
                    },
                    { 
                        type: "select",
                        name: "senderIDs",
                        label: "Sending Partner",
                        placeholder: "Connection's Sender",
                        list: [],
                        value: ""
                    },
                    {
                        type: "url",
                        name: "as2_url",
                        label: "AS2 URL",
                        placeholder: "i.e.  http://www.MyPartnerAS2Machine.com:10080",
                        value: ""
                    },
                    {
                        type: "text",
                        name: "subject",
                        label: "AS2 SMIME Subject",
                        placeholder: "i.e.  AS2 Transfer",
                        value: ""
                    },
                    {
                        type:"text",
                        name: "as2_mdn_to",
                        label: "MDN response To",
                        placeholder: "i.e.  datamanager@mypartner.com",
                        value: ""
                    },
                    {
                         type: "select",
                         name: "encrypt",
                         label: "Encryption Mode",
                         list: ["", "3DES", "CAST5", "RC2_CBC", "AES128", "AES192", "AES256"],
                         value: "3DES"
                    },
                    {
                          type: "select",
                          name: "sign",
                          label: "Signature Mode",
                          list: ["","SHA1","SHA256","SHA384","SHA512","RC4","MD5"],
                          value: "SHA1"
                    },
                    {
                        type: "text",
                        name: "as2_receipt_option",
                        label: "Asynchronous MDN server's URL",
                        placeholder: "i.e.  http://www.MyAS2Machine.com:10081",
                        value: ""
                    },
                    {
                        type: "text",
                        name: "as2_mdn_options",
                        label: "MDN option values for E-mail header",
                        placeholder: "i.e.  signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1",
                        value: "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1"
                    },
                    { 
                        type: "checkbox",
                        name: "prevent_canonicalization_for_mic",
                        label: "Prevent Canonicalization for MIC",
                        value: false
                    },
                    { 
                        type: "checkbox",
                        name: "remove_cms_algorithm_protection_attrib",
                        label: "Remove CMS Protection",
                        value: false
                    },
                    { 
                        type: "checkbox",
                        name: "rename_digest_to_old_name",
                        label: "Support Digest with Oldname",
                        value: false
                    },
                    { 
                        type: "select",
                        name: "content_transfer_encoding",
                        label: "Transfer Encoding",
                        list: ["8bit","binary"],
                        value: "8bit"
                    }
                ]
            }
        };
  },
  components: {
    ObjectList,
    ObjectEditor
  },
  mounted: async function() {
    var list=await Utils.Crud.getList('partner');
    console.log(list);
    this.schema.fields[1].list=Object.create(list);
    this.schema.fields[2].list=Object.create(list);
  },
  methods: {
        getNewObject: function() {
            var obj ={ "_id":null };
            this.schema.fields.forEach((f)=> obj[f.name]=f.value );
            return obj;
        },
        getList: async () => await Utils.Crud.getList('partnership').then((results) => {
          for(var i in results) {
              results[i]={
                 key: results[i], 
                 data: { "#": i,  "Connection ID": results[i] }, 
                 actions: { 
                      "Edit":  { name: 'ConnectionEditor', params:{id: i }}  
                      }  
              }
          }
          return results;
        }),
        getObject: async function(index) {
          var newObj = this.getNewObject();
          var item= await Utils.Crud.getObject("partnership",index,newObj);
          newObj._id = item._id;
          newObj.name = item.name;
          // Post process object to match form compatibility
          if(item.senderIDs != null) {
            newObj.senderIDs = item.senderIDs.name;
          }
          if(item.receiverIDs != null){
            newObj.receiverIDs = item.receiverIDs.name;
          }
          for(var i in item.attributes) {
            newObj[i]=item.attributes[i];
          }
          return newObj;
        },
        saveObject: async function(data) {
          data._prefix = `0=${data.senderIDs}&1=${data.receiverIDs}`;
          console.log(data);
          await Utils.Crud.saveObject('partnership',data);
          this.$router.push({name: 'Connections'});
        },
        deleteObject: async function(index) {
          await Utils.Crud.deleteObject('partnership',index);
          this.$router.push({name: 'Connections'});
        },
        revert: function() {
          this.$router.push({name: 'Connections'});
        }

  }
}
</script>